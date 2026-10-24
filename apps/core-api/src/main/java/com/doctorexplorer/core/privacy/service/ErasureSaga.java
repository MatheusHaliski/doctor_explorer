package com.doctorexplorer.core.privacy.service;

import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.SpecialtyClaims;
import com.doctorexplorer.core.identity.domain.AccountStatus;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.Credentials;
import com.doctorexplorer.core.identity.repo.EmailVerifications;
import com.doctorexplorer.core.identity.repo.LoginAudits;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.repo.UserPreferences;
import com.doctorexplorer.core.identity.service.MfaService;
import com.doctorexplorer.core.identity.service.UserKeyService;
import com.doctorexplorer.core.privacy.repo.DataExportRequests;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF10 · Eliminação após a carência, idempotente: anonimiza o PostgreSQL social, limpa Valkey (sessões e
 * limites) e destrói a DEK do usuário (crypto-shredding). Os passos de S3 e Cassandra entram com os
 * módulos de mídia (RF05) e mensagens (RF06) e ficam registrados aqui como pontos da saga. O recibo no
 * log não leva dado pessoal. Registros clínicos (clinical-api) ficam intocados: retenção de 20 anos.
 */
@Service
public class ErasureSaga {

    private static final Logger log = LoggerFactory.getLogger(ErasureSaga.class);

    public record Receipt(UUID userId, Instant erasedAt, List<String> steps) {}

    private final UserAccounts accounts;
    private final Credentials credentials;
    private final EmailVerifications verifications;
    private final UserPreferences preferences;
    private final LoginAudits loginAudits;
    private final ProfessionalProfiles profiles;
    private final SpecialtyClaims claims;
    private final DataExportRequests exports;
    private final MfaService mfa;
    private final UserKeyService userKeys;
    private final StringRedisTemplate redis;
    private final FindByIndexNameSessionRepository<?> sessions;

    public ErasureSaga(UserAccounts accounts, Credentials credentials, EmailVerifications verifications, UserPreferences preferences,
            LoginAudits loginAudits, ProfessionalProfiles profiles, SpecialtyClaims claims, DataExportRequests exports, MfaService mfa,
            UserKeyService userKeys, StringRedisTemplate redis, FindByIndexNameSessionRepository<?> sessions) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.verifications = verifications;
        this.preferences = preferences;
        this.loginAudits = loginAudits;
        this.profiles = profiles;
        this.claims = claims;
        this.exports = exports;
        this.mfa = mfa;
        this.userKeys = userKeys;
        this.redis = redis;
        this.sessions = sessions;
    }

    /** Elimina todas as contas com carência vencida. Devolve os recibos (sem dado pessoal). */
    public List<Receipt> runDue(Instant now) {
        List<UserAccount> due = accounts.findAll().stream()
                .filter(a -> a.getStatus() == AccountStatus.PENDING_DELETION && a.getGraceEndsAt() != null && a.getGraceEndsAt().isBefore(now))
                .toList();
        return due.stream().map(a -> erase(a.getId(), now)).toList();
    }

    /** Idempotente: rodar duas vezes para o mesmo usuário não falha nem reexpõe nada. */
    @Transactional
    public Receipt erase(UUID userId, Instant now) {
        UserAccount a = accounts.findById(userId).orElseThrow();
        if (a.getStatus() == AccountStatus.ANONYMOUS_LGPD_DELETED) {
            return new Receipt(userId, now, List.of("ja-eliminada"));
        }
        List<String> steps = new java.util.ArrayList<>();

        // 1. PostgreSQL social: credenciais, 2FA, verificação, preferências, perfil, especialidades, exportações, auditoria de login
        credentials.deleteById(userId); steps.add("credential");
        mfa.disable(userId); steps.add("mfa");
        verifications.deleteById(userId); steps.add("email_verification");
        preferences.deleteById(userId); steps.add("user_preference");
        claims.deleteByUserId(userId); profiles.deleteById(userId); steps.add("professional_profile");
        exports.deleteByUserId(userId); steps.add("data_export_request");
        loginAudits.deleteAll(loginAudits.findAll().stream().filter(l -> userId.equals(l.getUserId())).toList()); steps.add("login_audit");
        // consent_event e terms_acceptance ficam como prova de consentimento, ligados à lápide anônima (Art. 16, I)

        // 2. S3 (mídia) e Cassandra (mensagens, notificações): com os módulos RF05/RF06
        steps.add("s3:pendente-modulo-midia");
        steps.add("cassandra:pendente-modulo-mensagens");

        // 3. Valkey: sessões e limites
        sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
        redis.delete(List.of("rl:login:acct:" + a.getEmailNormalized(), "rl:mfa:acct:" + userId));
        steps.add("valkey");

        // 4. crypto-shredding: a DEK morre; backups com dados deste usuário ficam ilegíveis
        userKeys.shred(userId, now); steps.add("user_key:destruida");

        // 5. lápide anônima
        a.anonymize(now);
        accounts.save(a); steps.add("user_account:anonimizada");

        log.info("Eliminação concluída: usuário={} passos={}", userId, steps);
        return new Receipt(userId, now, steps);
    }
}
