package com.doctorexplorer.core.credentialing.web;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.credentialing.domain.CredentialCheck;
import com.doctorexplorer.core.credentialing.domain.SpecialtyClaim;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.credentialing.service.CredentialingService.ClaimInput;
import com.doctorexplorer.core.identity.web.SessionSupport;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF02 · /api/v1/credentials: envio de CRM/UF e RQE, consulta do selo e das evidências. */
@RestController
@RequestMapping("/api/v1/credentials")
public class CredentialController {

    public record SubmitRequest(String crmNumber, String crmUf, List<ClaimInput> specialties) {}

    private final CredentialingService credentialing;
    private final SessionSupport sessions;
    private final AccessService access;

    public CredentialController(CredentialingService credentialing, SessionSupport sessions, AccessService access) {
        this.credentialing = credentialing;
        this.sessions = sessions;
        this.access = access;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitRequest req) {
        var status = credentialing.submit(access.requireActive().userId(), req.crmNumber(), req.crmUf(), req.specialties());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(status));
    }

    @GetMapping
    Map<String, Object> status() {
        return view(credentialing.status(sessions.require().userId()));
    }

    private static Map<String, Object> view(CredentialingService.Status s) {
        return Map.of(
                "seal", s.seal().name(),
                "crmNumber", s.crmNumber() == null ? "" : s.crmNumber(),
                "crmUf", s.crmUf() == null ? "" : s.crmUf(),
                "specialties", s.specialties().stream().map(CredentialController::claim).toList(),
                "checks", s.checks().stream().map(CredentialController::check).toList());
    }

    private static Map<String, Object> claim(SpecialtyClaim c) {
        return Map.of("specialtyCode", c.getSpecialtyCode(), "rqe", c.getRqe(), "confirmed", c.isConfirmed());
    }

    private static Map<String, Object> check(CredentialCheck c) {
        return Map.of("source", c.getSource(), "checkedAt", c.getCheckedAt().toString(), "outcome", c.getOutcome().name(),
                "reason", c.getReasonPtBr() == null ? "" : c.getReasonPtBr());
    }
}
