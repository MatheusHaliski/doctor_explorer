package com.doctorexplorer.clinical.documents;

import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.documents.DocumentViews.CancelRequest;
import com.doctorexplorer.clinical.documents.DocumentViews.Content;
import com.doctorexplorer.clinical.documents.DocumentViews.DeliverRequest;
import com.doctorexplorer.clinical.documents.DocumentViews.Delivered;
import com.doctorexplorer.clinical.documents.DocumentViews.DocumentView;
import com.doctorexplorer.clinical.documents.DocumentViews.NewDocument;
import com.doctorexplorer.clinical.documents.DocumentViews.SignRequest;
import com.doctorexplorer.clinical.documents.DocumentViews.TemplateInput;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF09 · receita e pedido de exame do médico (via core-api, token interno). Não há rota de exclusão de assinado. */
@RestController
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/documents")
    ResponseEntity<DocumentView> create(Caller c, @RequestBody NewDocument body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.create(c, body));
    }

    @GetMapping("/documents")
    Map<String, Object> list(Caller c, @RequestParam(required = false) UUID patientId) {
        return Map.of("items", patientId == null ? documents.mine(c) : documents.byPatient(c, patientId));
    }

    @GetMapping("/documents/{id}")
    DocumentView read(Caller c, @PathVariable UUID id) {
        return documents.read(c, id);
    }

    @PutMapping("/documents/{id}")
    DocumentView save(Caller c, @PathVariable UUID id, @RequestBody Content body) {
        return documents.saveDraft(c, id, body);
    }

    @PostMapping("/documents/{id}/sign")
    DocumentView sign(Caller c, @PathVariable UUID id, @RequestBody SignRequest body) {
        return documents.sign(c, id, body.reviewedHash());
    }

    @GetMapping(value = "/documents/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> pdf(Caller c, @PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", ContentDisposition.attachment().filename("documento-" + id + ".pdf").build().toString())
                .header("Cache-Control", "no-store").body(documents.pdf(c, id));
    }

    @PostMapping("/documents/{id}/deliver")
    Delivered deliver(Caller c, @PathVariable UUID id, @RequestBody DeliverRequest body) {
        return documents.deliver(c, id, body.channel(), body.destination());
    }

    @PostMapping("/documents/{id}/cancel")
    DocumentView cancel(Caller c, @PathVariable UUID id, @RequestBody CancelRequest body) {
        return documents.cancel(c, id, body.reason());
    }

    @GetMapping("/document-templates")
    Map<String, Object> templates(Caller c, @RequestParam String kind) {
        return Map.of("items", documents.templates(c, kind));
    }

    @PostMapping("/document-templates")
    ResponseEntity<Map<String, Object>> saveTemplate(Caller c, @RequestBody TemplateInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.saveTemplate(c, body));
    }

    @DeleteMapping("/document-templates/{id}")
    ResponseEntity<Void> deleteTemplate(Caller c, @PathVariable UUID id) {
        documents.deleteTemplate(c, id);
        return ResponseEntity.noContent().build();
    }
}
