package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.service.LoginService;
import com.doctorexplorer.core.identity.service.MfaService;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF01 · /api/v1/me: sudo, senha, 2FA (TOTP + códigos de recuperação) e sessões. */
@RestController
@RequestMapping("/api/v1/me")
public class MeSecurityController {

    public record SudoRequest(String password, String code) {}

    public record PasswordChangeRequest(String currentPassword, String newPassword) {}

    public record CodeRequest(String code) {}

    private final UserAccounts accounts;
    private final LoginService login;
    private final MfaService mfa;
    private final SessionSupport sessions;

    public MeSecurityController(UserAccounts accounts, LoginService login, MfaService mfa, SessionSupport sessions) {
        this.accounts = accounts;
        this.login = login;
        this.mfa = mfa;
        this.sessions = sessions;
    }

    /** Modo sudo: senha (+ código se o 2FA estiver ativo). Vale 10 min nesta sessão. */
    @PostMapping("/sudo")
    Map<String, Object> sudo(@RequestBody SudoRequest req, HttpServletRequest request) {
        UUID userId = sessions.require().userId();
        if (!login.verifyForSudo(userId, req.password(), req.code())) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "sudo-falhou", "Não foi possível confirmar",
                    "Senha ou código incorretos.");
        }
        sessions.grantSudo(request);
        return Map.of("sudoUntilSeconds", SessionSupport.SUDO_TTL.toSeconds());
    }

    @PostMapping("/password")
    Map<String, Object> changePassword(@RequestBody PasswordChangeRequest req, HttpServletRequest request) {
        sessions.requireSudo(request);
        UUID userId = sessions.require().userId();
        List<String> problems = login.changePassword(userId, req.currentPassword(), req.newPassword());
        if (!problems.isEmpty()) {
            throw new ValidationProblemException(problems.stream().map(p -> new FieldProblem("newPassword", p)).toList());
        }
        int ended = sessions.endOthers(request, userId);
        return Map.of("otherSessionsEnded", ended);
    }

    @GetMapping("/mfa")
    Map<String, Object> mfaStatus() {
        UUID userId = sessions.require().userId();
        boolean enabled = mfa.isEnabled(userId);
        return Map.of("enabled", enabled, "recoveryCodesLeft", enabled ? mfa.remainingRecoveryCodes(userId) : 0);
    }

    @PostMapping("/mfa/totp/setup")
    Map<String, Object> setup(HttpServletRequest request) {
        sessions.requireSudo(request);
        UserAccount account = accounts.findById(sessions.require().userId()).orElseThrow();
        MfaService.Setup s = mfa.beginSetup(account);
        return Map.of("otpauthUri", s.otpauthUri(), "secret", s.secretBase32());
    }

    @PostMapping("/mfa/totp/confirm")
    Map<String, Object> confirm(@RequestBody CodeRequest req, HttpServletRequest request) {
        sessions.requireSudo(request);
        List<String> codes = mfa.confirmSetup(sessions.require().userId(), req.code());
        request.getSession(true).setAttribute(SessionSupport.MFA_ENABLED, true); // já vale nesta sessão
        return Map.of("enabled", true, "recoveryCodes", codes);
    }

    @PostMapping("/mfa/recovery-codes")
    Map<String, Object> regenerate(HttpServletRequest request) {
        sessions.requireSudo(request);
        UUID userId = sessions.require().userId();
        if (!mfa.isEnabled(userId)) throw new ApiProblemException(HttpStatus.CONFLICT, "2fa-inativo", "2FA inativo", "Ative o 2FA primeiro.");
        return Map.of("recoveryCodes", mfa.regenerateRecoveryCodes(userId));
    }

    @DeleteMapping("/mfa")
    ResponseEntity<Void> disable(HttpServletRequest request) {
        sessions.requireSudo(request);
        mfa.disable(sessions.require().userId());
        request.getSession(true).setAttribute(SessionSupport.MFA_ENABLED, false);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    Map<String, Object> listSessions(HttpServletRequest request) {
        return Map.of("sessions", sessions.list(request, sessions.require().userId()));
    }

    @DeleteMapping("/sessions/{handle}")
    ResponseEntity<Void> endSession(@PathVariable String handle, HttpServletRequest request) {
        boolean ok = sessions.end(sessions.require().userId(), handle);
        if (!ok) throw new ApiProblemException(HttpStatus.NOT_FOUND, "sessao-inexistente", "Sessão não encontrada", "Essa sessão já foi encerrada.");
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    Map<String, Object> endOtherSessions(HttpServletRequest request) {
        return Map.of("ended", sessions.endOthers(request, sessions.require().userId()));
    }
}
