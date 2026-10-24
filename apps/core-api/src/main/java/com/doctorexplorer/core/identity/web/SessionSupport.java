package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.common.Hashing;
import com.doctorexplorer.core.config.AbsoluteSessionTimeoutFilter;
import com.doctorexplorer.core.credentialing.domain.UserRole;
import com.doctorexplorer.core.credentialing.repo.UserRoles;
import com.doctorexplorer.core.identity.service.MfaService;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.service.RequestContext;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Abre, lê, lista e encerra a sessão opaca. Etapas intermediárias do login (2FA, reativação) ficam em
 * atributos próprios; só {@link #authenticate} grava o SecurityContext. O modo sudo vale 10 min.
 */
@Component
public class SessionSupport {

    public static final String PENDING_USER = "de.login.pendingUserId";
    public static final String PENDING_STEP = "de.login.pendingStep";
    public static final String SUDO_UNTIL = "de.sudo.until";
    public static final String USER_AGENT = "de.session.userAgent";
    public static final String IP_HASH = "de.session.ipHash";
    public static final String MFA_ENABLED = "de.session.mfaEnabled";
    public static final Duration SUDO_TTL = Duration.ofMinutes(10);

    public record SessionView(String handle, Instant createdAt, Instant lastAccessedAt, String userAgent, boolean current) {}

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final FindByIndexNameSessionRepository<?> sessionRepository;
    private final RequestContext ctx;
    private final UserRoles roles;
    private final MfaService mfa;

    public SessionSupport(FindByIndexNameSessionRepository<?> sessionRepository, RequestContext ctx, UserRoles roles, MfaService mfa) {
        this.sessionRepository = sessionRepository;
        this.ctx = ctx;
        this.roles = roles;
        this.mfa = mfa;
    }

    public void authenticate(HttpServletRequest request, HttpServletResponse response, UserAccount account) {
        HttpSession session = request.getSession(true);
        request.changeSessionId(); // fixação de sessão
        session.removeAttribute(PENDING_USER);
        session.removeAttribute(PENDING_STEP);
        session.setAttribute(AbsoluteSessionTimeoutFilter.STARTED_AT, Instant.now().toEpochMilli());
        session.setAttribute(USER_AGENT, ctx.userAgent(request));
        session.setAttribute(IP_HASH, ctx.ipHash(request));
        session.setAttribute(MFA_ENABLED, mfa.isEnabled(account.getId()));
        SessionPrincipal principal = new SessionPrincipal(account.getId(), account.getDisplayName(), account.getStatus());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(List.of(new SimpleGrantedAuthority("ROLE_PROFESSIONAL")));
        for (UserRole r : roles.findByUserId(account.getId())) authorities.add(new SimpleGrantedAuthority("ROLE_" + r.getRole().name()));
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(auth);
        SecurityContextHolder.setContext(sc);
        contextRepository.saveContext(sc, request, response);
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

    public void clearPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PENDING_USER);
            session.removeAttribute(PENDING_STEP);
        }
    }

    public Optional<SessionPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof SessionPrincipal p ? Optional.of(p) : Optional.empty();
    }

    public SessionPrincipal require() {
        return current().orElseThrow(() -> new ApiProblemException(HttpStatus.UNAUTHORIZED, "sem-sessao", "Sessão necessária", "Entre para continuar."));
    }

    public void grantSudo(HttpServletRequest request) {
        request.getSession(true).setAttribute(SUDO_UNTIL, Instant.now().plus(SUDO_TTL).toEpochMilli());
    }

    /** Ações sensíveis (senha, 2FA, exclusão, assinatura) exigem sudo recente: senha + código. */
    public void requireSudo(HttpServletRequest request) {
        require();
        HttpSession session = request.getSession(false);
        Object until = session == null ? null : session.getAttribute(SUDO_UNTIL);
        if (!(until instanceof Long u) || Instant.ofEpochMilli(u).isBefore(Instant.now())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sudo-necessario", "Confirme sua identidade",
                    "Para esta ação, confirme sua senha" + " (e o código do autenticador, se o 2FA estiver ativo) em POST /api/v1/me/sudo.");
        }
    }

    public void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    public List<SessionView> list(HttpServletRequest request, UUID userId) {
        String currentId = request.getSession(false) == null ? null : request.getSession(false).getId();
        return sessions(userId).entrySet().stream()
                .map(e -> new SessionView(handle(e.getKey()), e.getValue().getCreationTime(), e.getValue().getLastAccessedTime(),
                        e.getValue().getAttribute(USER_AGENT), e.getKey().equals(currentId)))
                .sorted((a, b) -> b.lastAccessedAt().compareTo(a.lastAccessedAt()))
                .toList();
    }

    public boolean end(UUID userId, String handle) {
        for (String id : sessions(userId).keySet()) {
            if (handle(id).equals(handle)) {
                sessionRepository.deleteById(id);
                return true;
            }
        }
        return false;
    }

    /** Encerra todas as outras sessões do usuário (troca de senha, "sair de todos os aparelhos"). */
    public int endOthers(HttpServletRequest request, UUID userId) {
        String currentId = request.getSession(false) == null ? null : request.getSession(false).getId();
        int n = 0;
        for (String id : sessions(userId).keySet()) {
            if (!id.equals(currentId)) {
                sessionRepository.deleteById(id);
                n++;
            }
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ? extends Session> sessions(UUID userId) {
        return (Map<String, ? extends Session>) sessionRepository.findByPrincipalName(userId.toString());
    }

    /** O id real da sessão nunca sai do servidor: o cliente vê só um apelido derivado por hash. */
    private static String handle(String sessionId) {
        return Hashing.sha256Hex("session-handle:" + sessionId).substring(0, 16);
    }
}
