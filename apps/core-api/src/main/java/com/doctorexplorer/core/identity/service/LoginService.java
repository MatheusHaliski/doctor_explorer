package com.doctorexplorer.core.identity.service;

import com.doctorexplorer.core.identity.domain.AccountStatus;
import com.doctorexplorer.core.identity.domain.LoginAudit;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.Credentials;
import com.doctorexplorer.core.identity.repo.LoginAudits;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.web.ApiProblemException;
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

    public LoginService(UserAccounts accounts, Credentials credentials, LoginAudits audits, PasswordEncoder encoder, LoginRateLimiter limiter) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.audits = audits;
        this.encoder = encoder;
        this.limiter = limiter;
    }

    @Transactional
    public Result passwordStep(String email, String password, String ipHash, String userAgent, boolean mfaEnabled) {
        String key = UserAccount.normalizeEmail(email == null ? "" : email);
        LoginRateLimiter.Decision d = limiter.check(key, ipHash);
        if (!d.allowed()) {
            audits.save(new LoginAudit(null, LoginAudit.Outcome.LOCKED, ipHash, userAgent));
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "muitas-tentativas", "Muitas tentativas",
                    "Aguarde " + d.retryAfterSeconds() + " s antes de tentar de novo.", Map.of("retryAfter", d.retryAfterSeconds()));
        }
        Optional<UserAccount> found = accounts.findByEmailNormalized(key);
        // Sempre roda o Argon2 (mesmo sem conta) para não revelar existência pelo tempo de resposta.
        String hash = found.flatMap(a -> credentials.findById(a.getId())).map(c -> c.getPasswordHash()).orElse(DUMMY_HASH);
        boolean ok = found.isPresent() && encoder.matches(password == null ? "" : password, hash);
        if (!ok || found.get().getStatus() == AccountStatus.ANONYMOUS_LGPD_DELETED || found.get().getStatus() == AccountStatus.SUSPENDED) {
            limiter.recordFailure(key, ipHash);
            audits.save(new LoginAudit(found.map(UserAccount::getId).orElse(null), LoginAudit.Outcome.BAD_CREDENTIALS, ipHash, userAgent));
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "credenciais", "Não foi possível entrar", "E-mail ou senha incorretos.");
        }
        UserAccount account = found.get();
        limiter.recordSuccess(key);
        if (mfaEnabled) {
            audits.save(new LoginAudit(account.getId(), LoginAudit.Outcome.MFA_REQUIRED, ipHash, userAgent));
            return new Result(Next.MFA, account);
        }
        return finish(account, ipHash, userAgent);
    }

    /** Depois da senha (e do 2FA, quando houver): decide entre entrar ou oferecer reativação. */
    @Transactional
    public Result finish(UserAccount account, String ipHash, String userAgent) {
        audits.save(new LoginAudit(account.getId(), LoginAudit.Outcome.SUCCESS, ipHash, userAgent));
        if (account.getStatus() == AccountStatus.PENDING_DELETION) return new Result(Next.REACTIVATE, account);
        return new Result(Next.AUTHENTICATED, account);
    }

    @Transactional
    public UserAccount reactivate(UUID userId) {
        UserAccount account = accounts.findById(userId).orElseThrow();
        account.reactivate();
        return accounts.save(account);
    }

    @Transactional
    public void logout(UUID userId, String ipHash, String userAgent) {
        audits.save(new LoginAudit(userId, LoginAudit.Outcome.LOGOUT, ipHash, userAgent));
    }
}
