package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.service.LoginService;
import com.doctorexplorer.core.identity.service.RegistrationService;
import com.doctorexplorer.core.identity.service.RegistrationService.RegisterCommand;
import com.doctorexplorer.core.identity.service.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF01 · /api/v1/auth: CSRF, cadastro, login em etapas, reativação, logout e sessão. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 254, message = "E-mail longo demais.") String email,
            @NotBlank(message = "Informe seu nome.") @Size(min = 2, max = 120, message = "O nome precisa ter de 2 a 120 caracteres.") String displayName,
            @NotNull(message = "Informe sua data de nascimento.") @Past(message = "A data de nascimento precisa estar no passado.") LocalDate birthDate,
            @NotBlank(message = "Informe uma senha.") String password,
            boolean acceptTerms,
            boolean acceptPrivacy,
            @NotBlank(message = "Versão dos Termos ausente.") String termsVersion,
            @NotBlank(message = "Versão da Política ausente.") String privacyVersion) {}

    public record LoginRequest(String email, String password) {}

    public record UserView(UUID id, String displayName, String email, String status) {
        static UserView of(UserAccount a) {
            return new UserView(a.getId(), a.getDisplayName(), a.getEmail(), a.getStatus().name());
        }
    }

    private final RegistrationService registration;
    private final LoginService login;
    private final SessionSupport sessions;
    private final RequestContext ctx;

    public AuthController(RegistrationService registration, LoginService login, SessionSupport sessions, RequestContext ctx) {
        this.registration = registration;
        this.login = login;
        this.sessions = sessions;
        this.ctx = ctx;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req, HttpServletRequest request, HttpServletResponse response) {
        RegisterCommand cmd = new RegisterCommand(req.email(), req.displayName(), req.birthDate(), req.password(),
                req.acceptTerms(), req.acceptPrivacy(), req.termsVersion(), req.privacyVersion());
        UserAccount account = registration.register(req, cmd, ctx.ipHash(request), ctx.userAgent(request));
        sessions.authenticate(request, response, account);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", UserView.of(account), "next", "verify-email"));
    }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        // 2FA entra no passo 3; até lá, mfaEnabled = false.
        LoginService.Result r = login.passwordStep(req.email(), req.password(), ctx.ipHash(request), ctx.userAgent(request), false);
        return switch (r.next()) {
            case MFA -> {
                sessions.setPending(request, r.account().getId(), "2fa");
                yield Map.of("next", "2fa");
            }
            case REACTIVATE -> {
                sessions.setPending(request, r.account().getId(), "reactivate");
                yield Map.of("next", "reactivate", "graceEndsAt", String.valueOf(r.account().getGraceEndsAt()));
            }
            case AUTHENTICATED -> {
                sessions.authenticate(request, response, r.account());
                yield Map.of("user", UserView.of(r.account()));
            }
        };
    }

    @PostMapping("/reactivate")
    Map<String, Object> reactivate(HttpServletRequest request, HttpServletResponse response) {
        UUID userId = sessions.requirePending(request, "reactivate");
        UserAccount account = login.reactivate(userId);
        sessions.authenticate(request, response, account);
        return Map.of("user", UserView.of(account));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.current().ifPresent(p -> login.logout(p.userId(), ctx.ipHash(request), ctx.userAgent(request)));
        sessions.invalidate(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    Map<String, Object> session() {
        SessionPrincipal p = sessions.require();
        return Map.of("userId", p.userId(), "displayName", p.displayName(), "status", p.status().name());
    }
}
