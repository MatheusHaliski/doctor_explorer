package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.service.RegistrationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF01 · /api/v1/me: perfil básico e confirmação de e-mail. Senha, 2FA, sessões e exclusão entram nos passos 3 e 5. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    public record VerifyEmailRequest(String code) {}

    private final UserAccounts accounts;
    private final RegistrationService registration;
    private final SessionSupport sessions;

    public MeController(UserAccounts accounts, RegistrationService registration, SessionSupport sessions) {
        this.accounts = accounts;
        this.registration = registration;
        this.sessions = sessions;
    }

    @GetMapping
    Map<String, Object> me() {
        SessionPrincipal p = sessions.require();
        UserAccount a = accounts.findById(p.userId()).orElseThrow();
        return Map.of("id", a.getId(), "displayName", a.getDisplayName(), "email", a.getEmail(),
                "status", a.getStatus().name(), "emailVerified", a.getActivatedAt() != null);
    }

    @PostMapping("/verify-email")
    Map<String, Object> verifyEmail(@RequestBody VerifyEmailRequest req) {
        SessionPrincipal p = sessions.require();
        UserAccount a = registration.verifyEmail(p.userId(), req.code());
        return Map.of("status", a.getStatus().name());
    }

    @PostMapping("/verify-email/resend")
    ResponseEntity<Void> resend() {
        registration.resendVerification(sessions.require().userId());
        return ResponseEntity.accepted().build();
    }
}
