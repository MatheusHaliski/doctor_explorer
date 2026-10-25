package com.doctorexplorer.core.identity.service;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.identity.domain.AccountStatus;
import com.doctorexplorer.core.identity.domain.Credential;
import com.doctorexplorer.core.identity.domain.LoginAudit;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.Credentials;
import com.doctorexplorer.core.identity.repo.LoginAudits;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.web.ApiProblemException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF01 · Login em etapas. Mensagem neutra em qualquer falha de credencial; limite por conta e IP;
 * o estado intermediário (2FA pendente, oferta de reativação) fica na sessão, nunca no cliente.
 */
@Service
public class LoginService {

    public enum Next { AUTHENTICATED, MFA, REACTIVATE }

    public record Result(Next next, UserAccount account) {}

    private static final String DUMMY_HASH = "{argon2}$argon2id$v=19$m=16384,t=2,p=1$c2FsdHNhbHRzYWx0c2FsdA$Y2lwaGVydGV4dGNpcGhlcnRleHRjaXBoZXJ0ZXh0";

    private final UserAccounts accounts;
    private final Credentials credentials;
    private final LoginAudits audits;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter limiter;
    private final MfaService mfa;
    private final PasswordPolicy passwordPolicy;

    private final AuditLog audit;

    public LoginService(UserAccounts accounts, Credentials credentials, LoginAudits audits, PasswordEncoder encoder,
            LoginRateLimiter limiter, MfaService mfa, PasswordPolicy passwordPolicy, AuditLog audit) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.audits = audits;
        this.encoder = encoder;
        this.limiter = limiter;
        this.mfa = mfa;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    @Transactional
    public Result passwordStep(String email, String password, String ipHash, String userAgent) {
        String key = UserAccount.normalizeEmail(email == null ? "" : email);
        LoginRateLimiter.Decision d = limiter.check(key, ipHash);
        if (!d.allowed()) {
            track(new LoginAudit(null, LoginAudit.Outcome.LOCKED, ipHash, userAgent), ipHash);
            throw tooMany(d.retryAfterSeconds());
        }
        Optional<UserAccount> found = accounts.findByEmailNormalized(key);
        // Sempre roda o Argon2 (mesmo sem conta) para não revelar existência pelo tempo de resposta.
        String hash = found.flatMap(a -> credentials.findById(a.getId())).map(Credential::getPasswordHash).orElse(DUMMY_HASH);
        boolean ok = found.isPresent() && encoder.matches(password == null ? "" : password, hash);
        if (!ok || found.get().getStatus() == AccountStatus.ANONYMOUS_LGPD_DELETED || found.get().getStatus() == AccountStatus.SUSPENDED) {
            limiter.recordFailure(key, ipHash);
            track(new LoginAudit(found.map(UserAccount::getId).orElse(null), LoginAudit.Outcome.BAD_CREDENTIALS, ipHash, userAgent), ipHash);
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "credenciais", "Não foi possível entrar", "E-mail ou senha incorretos.");
        }
        UserAccount account = found.get();
        limiter.recordSuccess(key);
        if (mfa.isEnabled(account.getId())) {
            track(new LoginAudit(account.getId(), LoginAudit.Outcome.MFA_REQUIRED, ipHash, userAgent), ipHash);
            return new Result(Next.MFA, account);
        }
        return finish(account, ipHash, userAgent);
    }

    /** Segunda etapa: código TOTP ou de recuperação. 5 falhas em 5 min encerram a etapa. */
    @Transactional
    public Result mfaStep(UUID userId, String code, String ipHash, String userAgent) {
        UserAccount account = accounts.findById(userId).orElseThrow();
        LoginRateLimiter.Decision d = limiter.checkMfa(userId.toString());
        if (!d.allowed()) throw tooMany(d.retryAfterSeconds());
        MfaService.Verification v = mfa.verify(userId, code);
        if (v == MfaService.Verification.INVALID) {
            limiter.recordMfaFailure(userId.toString());
            track(new LoginAudit(userId, LoginAudit.Outcome.MFA_FAILED, ipHash, userAgent), ipHash);
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "codigo-2fa", "Código incorreto",
                    "Confira o código do aplicativo autenticador ou use um código de recuperação.");
        }
        limiter.clearMfa(userId.toString());
        return finish(account, ipHash, userAgent);
    }

    /** Depois da senha (e do 2FA, quando houver): decide entre entrar ou oferecer reativação. */
    @Transactional
    public Result finish(UserAccount account, String ipHash, String userAgent) {
        track(new LoginAudit(account.getId(), LoginAudit.Outcome.SUCCESS, ipHash, userAgent), ipHash);
        if (account.getStatus() == AccountStatus.PENDING_DELETION) return new Result(Next.REACTIVATE, account);
        return new Result(Next.AUTHENTICATED, account);
    }

    /** Modo sudo: senha e, se o 2FA estiver ativo, código. Não conta no limite de login. */
    @Transactional(readOnly = true)
    public boolean verifyForSudo(UUID userId, String password, String code) {
        Credential c = credentials.findById(userId).orElse(null);
        if (c == null || !encoder.matches(password == null ? "" : password, c.getPasswordHash())) return false;
        if (!mfa.isEnabled(userId)) return true;
        return mfa.verify(userId, code) != MfaService.Verification.INVALID;
    }

    @Transactional
    public List<String> changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findById(userId).orElseThrow();
        Credential c = credentials.findById(userId).orElseThrow();
        if (!encoder.matches(currentPassword == null ? "" : currentPassword, c.getPasswordHash())) {
            return List.of("A senha atual não confere.");
        }
        List<String> problems = passwordPolicy.validate(newPassword, account.getEmail(), account.getDisplayName());
        if (!problems.isEmpty()) return problems;
        c.change(encoder.encode(newPassword));
        credentials.save(c);
        audit.record(AuditRecord.of("auth.password_change").actor(userId, "").resource("user_account", userId));
        return List.of();
    }

    @Transactional
    public UserAccount reactivate(UUID userId) {
        UserAccount account = accounts.findById(userId).orElseThrow();
        account.reactivate();
        return accounts.save(account);
    }

    @Transactional
    public void logout(UUID userId, String ipHash, String userAgent) {
        track(new LoginAudit(userId, LoginAudit.Outcome.LOGOUT, ipHash, userAgent), ipHash);
    }

    /** login_audit (alertas e limite de tentativas) + trilha encadeada da auditoria (RF13). */
    private void track(LoginAudit a, String ipHash) {
        audits.save(a);
        if (a.getOutcome() == LoginAudit.Outcome.MFA_REQUIRED) return; // etapa intermediária, não é desfecho
        String action = a.getOutcome() == LoginAudit.Outcome.LOGOUT ? "auth.logout" : "auth.login";
        audit.record(AuditRecord.of(action).actor(a.getUserId(), "").resource("user_account", a.getUserId())
                .outcome(a.getOutcome().name().toLowerCase(java.util.Locale.ROOT)).ip(ipHash));
    }

    private static ApiProblemException tooMany(long retryAfter) {
        return new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "muitas-tentativas", "Muitas tentativas",
                "Aguarde " + retryAfter + " s antes de tentar de novo.", Map.of("retryAfter", retryAfter));
    }
}
