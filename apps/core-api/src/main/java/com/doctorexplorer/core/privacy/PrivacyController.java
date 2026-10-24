package com.doctorexplorer.core.privacy;

import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.service.RequestContext;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.privacy.domain.ConsentPurpose;
import com.doctorexplorer.core.privacy.domain.DataExportRequest;
import com.doctorexplorer.core.privacy.domain.DpoTicket;
import com.doctorexplorer.core.privacy.service.PrivacyService;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF10 · /api/v1/privacy: consentimentos, seus dados, exportação, compartilhamento, Encarregado. */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    public record ConsentUpdate(boolean granted) {}

    public record TicketRequest(String type, String message) {}

    private final PrivacyService privacy;
    private final SessionSupport sessions;
    private final RequestContext ctx;

    public PrivacyController(PrivacyService privacy, SessionSupport sessions, RequestContext ctx) {
        this.privacy = privacy;
        this.sessions = sessions;
        this.ctx = ctx;
    }

    @GetMapping("/consents")
    Map<String, Object> consents() {
        UUID userId = sessions.require().userId();
        return Map.of("consents", privacy.consents(userId).stream().map(c -> Map.of(
                "purpose", c.purpose().name(), "title", c.purpose().title, "description", c.purpose().description,
                "legalBasis", c.purpose().legalBasis, "granted", c.granted(), "changedAt", c.changedAt() == null ? "" : c.changedAt().toString())).toList());
    }

    @PutMapping("/consents/{purpose}")
    Map<String, Object> setConsent(@PathVariable String purpose, @RequestBody ConsentUpdate body, HttpServletRequest request) {
        UUID userId = sessions.require().userId();
        ConsentPurpose p;
        try {
            p = ConsentPurpose.valueOf(purpose.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiProblemException(HttpStatus.NOT_FOUND, "finalidade", "Finalidade desconhecida", "Finalidades: " + List.of(ConsentPurpose.values()));
        }
        var c = privacy.setConsent(userId, p, body.granted(), ctx.ipHash(request));
        return Map.of("purpose", c.purpose().name(), "granted", c.granted(), "changedAt", c.changedAt().toString());
    }

    @GetMapping("/consents/history")
    Map<String, Object> history() {
        return Map.of("events", privacy.consentHistory(sessions.require().userId()).stream().map(e -> Map.of(
                "purpose", e.getPurpose().name(), "granted", e.isGranted(), "textVersion", e.getTextVersion(), "at", e.getOccurredAt().toString())).toList());
    }

    @GetMapping("/data")
    Map<String, Object> myData() {
        return privacy.myData(sessions.require().userId());
    }

    @GetMapping("/sharing")
    Map<String, Object> sharing() {
        sessions.require();
        return Map.of("operators", PrivacyService.OPERATORS, "legalBasis", "Art. 18, VII");
    }

    @PostMapping("/export")
    ResponseEntity<Map<String, Object>> requestExport() {
        DataExportRequest r = privacy.requestExport(sessions.require().userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", r.getId(), "state", r.getState().name(),
                "formatVersion", r.getFormatVersion(), "expiresAt", r.getExpiresAt().toString(), "downloadUrl", "/api/v1/privacy/export/" + r.getId()));
    }

    @GetMapping(value = "/export/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> download(@PathVariable UUID id) {
        DataExportRequest r = privacy.download(sessions.require().userId(), id);
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"doctor-explorer-meus-dados-v" + r.getFormatVersion() + ".json\"")
                .body(r.getPayload());
    }

    @PostMapping("/dpo-requests")
    ResponseEntity<Map<String, Object>> openTicket(@RequestBody TicketRequest req) {
        UUID userId = sessions.require().userId();
        DpoTicket.Type type;
        try {
            type = DpoTicket.Type.valueOf(req.type() == null ? "OTHER" : req.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = DpoTicket.Type.OTHER;
        }
        DpoTicket t = privacy.openTicket(userId, type, req.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket(t));
    }

    @GetMapping("/dpo-requests")
    Map<String, Object> myTickets() {
        return Map.of("tickets", privacy.myTickets(sessions.require().userId()).stream().map(PrivacyController::ticket).toList());
    }

    static Map<String, Object> ticket(DpoTicket t) {
        return Map.of("id", t.getId(), "protocol", t.getProtocol(), "type", t.getType().name(), "state", t.getState().name(),
                "openedAt", t.getOpenedAt().toString(), "dueAt", t.getDueAt().toString(), "answer", t.getAnswer() == null ? "" : t.getAnswer());
    }

    static Map<String, Object> deletionView(UserAccount a) {
        return Map.of("status", a.getStatus().name(), "graceEndsAt", String.valueOf(a.getGraceEndsAt()),
                "note", "Registros clínicos ficam guardados por 20 anos por obrigação legal (Lei 13.787/2018).");
    }
}
