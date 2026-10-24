package com.doctorexplorer.core.privacy;

import com.doctorexplorer.core.privacy.domain.DpoTicket;
import com.doctorexplorer.core.privacy.service.PrivacyService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF10 · /api/v1/privacy/dpo: fila do Encarregado. Só DPO com 2FA (SecurityConfig). Toda resposta é auditada (RF13). */
@RestController
@RequestMapping("/api/v1/privacy/dpo")
public class DpoController {

    public record AnswerRequest(String answer) {}

    private final PrivacyService privacy;

    public DpoController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping("/tickets")
    Map<String, Object> tickets() {
        return Map.of("tickets", privacy.openTickets().stream().map(t -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>(PrivacyController.ticket(t));
            m.put("message", t.getMessage());
            m.put("userId", t.getUserId() == null ? "" : t.getUserId().toString());
            return m;
        }).toList());
    }

    @PostMapping("/tickets/{id}/answer")
    Map<String, Object> answer(@PathVariable UUID id, @RequestBody AnswerRequest req) {
        DpoTicket t = privacy.answerTicket(id, req.answer());
        return PrivacyController.ticket(t);
    }
}
