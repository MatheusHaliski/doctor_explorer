package com.doctorexplorer.core.internal;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.web.ApiProblemException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas entre serviços (ADR-008). A chave pública do token interno é pública (a clinical-api a busca para validar).
 * O retorno de auditoria só aceita o próprio token interno que a core-api emitiu para aquela chamada: o ator é
 * sempre quem está no token, e só ações {@code clinical.*} entram. Em produção /api/v1/internal/** também fica
 * fora do balanceador público.
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    public record AuditRequest(String action, String resourceType, String resourceId, String patientRef, String outcome, String justification,
            Map<String, String> details) {}

    private final InternalTokens tokens;
    private final AuditLog audit;

    public InternalController(InternalTokens tokens, AuditLog audit) {
        this.tokens = tokens;
        this.audit = audit;
    }

    @GetMapping("/public-key")
    Map<String, Object> publicKey() {
        return Map.of("alg", "Ed25519", "spki", tokens.publicKeyBase64(), "ttlSeconds", InternalTokens.TTL.toSeconds());
    }

    @PostMapping("/audit")
    ResponseEntity<Map<String, Object>> audit(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody AuditRequest r) {
        String raw = authorization != null && authorization.startsWith("DE-Internal ") ? authorization.substring(12).trim() : null;
        InternalTokens.Claims c = tokens.verify(raw, InternalTokens.CLINICAL).orElseThrow(() ->
                new ApiProblemException(HttpStatus.UNAUTHORIZED, "token-interno-invalido", "Token interno inválido", "Chamada entre serviços sem token válido."));
        if (r.action() == null || !r.action().matches("clinical\\.[a-z_.]{3,60}")) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "acao-invalida", "Ação inválida", "Só ações clinical.* entram por esta rota.");
        }
        AuditRecord rec = AuditRecord.of(r.action()).actor(c.sub(), "PHYSICIAN").resource(r.resourceType(), r.resourceId()).patient(r.patientRef())
                .outcome(r.outcome() == null || r.outcome().isBlank() ? "success" : r.outcome()).justification(r.justification()).detail("via", "clinical-api");
        if (r.details() != null) r.details().forEach(rec::detail);
        var e = audit.record(rec);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", e.id(), "seq", e.seq()));
    }
}
