package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.audit.AuditService;
import com.doctorexplorer.core.audit.AuditSigner;
import com.doctorexplorer.core.identity.web.SessionSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF13 · exportações de quem pediu (plataforma ou instituição) e a chave pública para conferir a assinatura.
 * A chave pública não exige login: o auditor externo confere o CSV com OpenSSL.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditExportController {

    private final AuditService audit;
    private final AuditSigner signer;
    private final SessionSupport sessions;

    public AuditExportController(AuditService audit, AuditSigner signer, SessionSupport sessions) {
        this.audit = audit;
        this.signer = signer;
        this.sessions = sessions;
    }

    @GetMapping("/exports")
    Map<String, Object> mine() {
        return Map.of("exports", audit.myExports(sessions.require().userId()).stream().map(AuditViews::export).toList());
    }

    @GetMapping("/exports/{id}/download")
    Map<String, Object> download(@PathVariable UUID id) {
        AuditService.Download d = audit.download(sessions.require().userId(), id);
        Map<String, Object> m = new LinkedHashMap<>(AuditViews.export(d.export()));
        m.put("csvUrl", d.csvUrl().toString());
        m.put("signatureUrl", d.signatureUrl().toString());
        m.put("urlTtlSeconds", AuditService.DOWNLOAD_TTL.toSeconds());
        m.put("verify", "openssl pkeyutl -verify -pubin -inkey doctor-explorer-auditoria.pem -rawin -in auditoria.csv -sigfile auditoria.sig.bin "
                + "(antes: base64 -d auditoria.csv.sig > auditoria.sig.bin)");
        return m;
    }

    @GetMapping(value = "/public-key", produces = "application/x-pem-file")
    ResponseEntity<String> publicKey() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header("Content-Disposition", "inline; filename=\"doctor-explorer-auditoria.pem\"").body(signer.publicKeyPem());
    }
}
