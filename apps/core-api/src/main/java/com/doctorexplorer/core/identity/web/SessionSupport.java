package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.config.AbsoluteSessionTimeoutFilter;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/**
 * Abre, lê e encerra a sessão opaca. Etapas intermediárias do login (2FA, reativação) ficam em atributos
 * próprios; só {@link #authenticate} grava o SecurityContext.
 */
@Component
public class SessionSupport {

    public static final String PENDING_USER = "de.login.pendingUserId";
    public static final String PENDING_STEP = "de.login.pendingStep";

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public void authenticate(HttpServletRequest request, HttpServletResponse response, UserAccount account) {
        HttpSession session = request.getSession(true);
        request.changeSessionId(); // fixação de sessão
        session.removeAttribute(PENDING_USER);
        session.removeAttribute(PENDING_STEP);
        session.setAttribute(AbsoluteSessionTimeoutFilter.STARTED_AT, Instant.now().toEpochMilli());
        SessionPrincipal principal = new SessionPrincipal(account.getId(), account.getDisplayName(), account.getStatus());
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_PROFESSIONAL")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, request, response);
        // índice por usuário para listar/encerrar sessões (repositório indexed do Spring Session)
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, account.getId().toString());
    }

    public void setPending(HttpServletRequest request, UUID userId, String step) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(PENDING_USER, userId.toString());
        session.setAttribute(PENDING_STEP, step);
    }

    public UUID requirePending(HttpServletRequest request, String step) {
        HttpSession session = request.getSession(false);
        if (session == null || !step.equals(session.getAttribute(PENDING_STEP)) || !(session.getAttribute(PENDING_USER) instanceof String id)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "etapa-invalida", "Etapa inválida", "Comece o login de novo.");
        }
        return UUID.fromString(id);
    }

    public Optional<SessionPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof SessionPrincipal p ? Optional.of(p) : Optional.empty();
    }

    public SessionPrincipal require() {
        return current().orElseThrow(() -> new ApiProblemException(HttpStatus.UNAUTHORIZED, "sem-sessao", "Sessão necessária", "Entre para continuar."));
    }

    public void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }
}
