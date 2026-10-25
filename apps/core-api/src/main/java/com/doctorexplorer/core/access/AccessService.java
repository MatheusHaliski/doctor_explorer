package com.doctorexplorer.core.access;

import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.identity.domain.AccountStatus;
import com.doctorexplorer.core.identity.web.SessionPrincipal;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.web.ApiProblemException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * RF12 · Checagens de domínio que a cadeia de filtros não cobre: selo, estado da conta e dono do recurso.
 * Papel sem permissão → 403 com motivo em pt-BR; recurso de outro dono → 404 sem revelar existência.
 */
@Service
public class AccessService {

    private final SessionSupport sessions;
    private final CredentialingService credentialing;

    public AccessService(SessionSupport sessions, CredentialingService credentialing) {
        this.sessions = sessions;
        this.credentialing = credentialing;
    }

    /** Conta ativa (e-mail confirmado): necessário para qualquer escrita além do próprio cadastro. */
    public SessionPrincipal requireActive() {
        SessionPrincipal p = sessions.require();
        if (p.status() != AccountStatus.ACTIVE) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "conta-inativa", "Confirme seu e-mail",
                    "Confirme o e-mail para usar esta função.");
        }
        return p;
    }

    /** Publicar, comentar, conversar, chamar, registrar SOAP, prescrever e aparecer no diretório exigem selo VERIFIED. */
    public SessionPrincipal requireVerifiedSeal() {
        SessionPrincipal p = requireActive();
        if (!credentialing.isVerified(p.userId())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "selo-necessario", "Selo CRM necessário",
                    "Esta função fica disponível depois que seu CRM for verificado. Envie CRM/UF e RQE em /api/v1/credentials.");
        }
        return p;
    }

    /** Leitura de grupos e feed: médico verificado, ou moderador/admin da plataforma (para moderar). */
    public SessionPrincipal requireVerifiedSealOrModerator() {
        SessionPrincipal p = requireActive();
        if (credentialing.isVerified(p.userId()) || isPlatformModerator()) return p;
        throw new ApiProblemException(HttpStatus.FORBIDDEN, "selo-necessario", "Selo CRM necessário",
                "Esta função fica disponível depois que seu CRM for verificado. Envie CRM/UF e RQE em /api/v1/credentials.");
    }

    /** Igual a {@link #isPlatformModerator()}, mas seguro para rotas públicas (sem sessão → false). */
    public boolean isPlatformModeratorSafe() {
        return sessions.current().isPresent() && isPlatformModerator();
    }

    public boolean isPlatformModerator() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MODERATOR") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Dono do recurso ou 404: não revela se o recurso existe. */
    public void requireOwnerOr404(UUID ownerId) {
        SessionPrincipal p = sessions.require();
        if (!p.userId().equals(ownerId)) {
            throw new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "O recurso não existe ou você não tem acesso.");
        }
    }

    /** Papéis globais da sessão, sem o prefixo ROLE_ (PROFESSIONAL, MODERATOR, ADMIN, DPO, TECHNICAL_DIRECTOR). */
    public java.util.Set<String> globalRoles() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return java.util.Set.of();
        return auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Papéis sensíveis (gestor, auditor, moderador, admin, Encarregado) só agem com 2FA ativo na sessão (RF12, RF13).
     * Para as rotas /admin isso já vem do SecurityConfig; as rotas por instituição chamam este método.
     */
    public void requireMfaSession(jakarta.servlet.http.HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(com.doctorexplorer.core.identity.web.SessionSupport.MFA_ENABLED))) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "2fa-necessario", "Verificação em duas etapas necessária",
                    "Painéis de gestão e auditoria exigem 2FA ativo. Ative em Sessões e segurança, saia e entre de novo.");
        }
    }
}
