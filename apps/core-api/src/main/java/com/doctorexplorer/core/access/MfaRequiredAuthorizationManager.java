package com.doctorexplorer.core.access;

import com.doctorexplorer.core.identity.web.SessionSupport;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * RF12 · Rotas de moderação, administração e do Encarregado: papel certo <b>e</b> 2FA ativo na sessão.
 * Quem tem o papel mas não ativou o 2FA recebe 403 até ativar (regra: "não usa a conta sem 2FA ativo").
 */
public class MfaRequiredAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final Set<String> allowedRoles;

    public MfaRequiredAuthorizationManager(String... roles) {
        this.allowedRoles = Set.of(roles);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) return new AuthorizationDecision(false);
        boolean hasRole = auth.getAuthorities().stream().anyMatch(a -> allowedRoles.contains(a.getAuthority().replace("ROLE_", "")));
        if (!hasRole) return new AuthorizationDecision(false);
        Object mfa = context.getRequest().getSession(false) == null ? null : context.getRequest().getSession(false).getAttribute(SessionSupport.MFA_ENABLED);
        return new AuthorizationDecision(Boolean.TRUE.equals(mfa));
    }
}
