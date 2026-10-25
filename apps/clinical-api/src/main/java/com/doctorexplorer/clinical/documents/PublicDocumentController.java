package com.doctorexplorer.clinical.documents;

import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.documents.DocumentViews.PublicView;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas sem login, sempre via core-api com o token interno "PUBLIC": validador da farmácia (/validar/{codigo} no
 * web) e link de uso único do paciente (/receita/{token}).
 */
@RestController
public class PublicDocumentController {

    private final DocumentService documents;

    public PublicDocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/public/documents/{code}")
    PublicView validate(Caller c, @PathVariable String code) {
        return documents.validate(c, code);
    }

    @GetMapping(value = "/public/deliveries/{token}", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> open(Caller c, @PathVariable String token) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", ContentDisposition.attachment().filename("documento-medico.pdf").build().toString())
                .header("Cache-Control", "no-store").body(documents.openDelivery(c, token));
    }
}
