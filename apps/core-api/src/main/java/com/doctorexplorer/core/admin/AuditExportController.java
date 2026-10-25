package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.audit.AuditSigner;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF13 · chave pública para conferir a assinatura das exportações. Não exige login: o auditor externo confere o
 * CSV com OpenSSL. Listar e baixar exportações fica nos painéis (plataforma e instituição), que conferem de novo
 * papel, vínculo e 2FA a cada download.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditExportController {

    private final AuditSigner signer;

    public AuditExportController(AuditSigner signer) {
        this.signer = signer;
    }

    @GetMapping(value = "/public-key", produces = "application/x-pem-file")
    ResponseEntity<String> publicKey() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header("Content-Disposition", "inline; filename=\"doctor-explorer-auditoria.pem\"").body(signer.publicKeyPem());
    }
}
