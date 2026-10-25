package com.doctorexplorer.core.audit;

import com.doctorexplorer.core.common.UuidV7;
import com.doctorexplorer.core.identity.web.SessionPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Ponto único para registrar auditoria (RF13). Todo evento entra na cadeia "platform"; evento de instituição
 * entra também na cadeia dela. Falha ao gravar propaga a exceção: ação sensível sem rastro não acontece.
 */
@Service
public class AuditLog {

    public static final String PLATFORM = "platform";

    private final CassandraAuditStore store;

    public AuditLog(CassandraAuditStore store) {
        this.store = store;
    }

    public static String scopeOf(UUID institutionId) {
        return "inst:" + institutionId;
    }

    public AuditEvent record(AuditRecord r) {
        if (r.actorId == null) fillActorFromSecurityContext(r);
        UUID id = UuidV7.generate();
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuditEvent platform = store.append(PLATFORM, r, id, at);
        if (r.institutionId != null) store.append(scopeOf(r.institutionId), r, id, at);
        return platform;
    }

    private static void fillActorFromSecurityContext(AuditRecord r) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionPrincipal p) {
            String roles = String.join(",", auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).sorted().toList());
            r.actor(p.userId(), roles);
        }
    }
}
