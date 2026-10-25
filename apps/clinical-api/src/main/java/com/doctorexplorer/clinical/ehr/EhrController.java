package com.doctorexplorer.clinical.ehr;

import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.ehr.EhrViews.AmendRequest;
import com.doctorexplorer.clinical.ehr.EhrViews.CompositionView;
import com.doctorexplorer.clinical.ehr.EhrViews.Draft;
import com.doctorexplorer.clinical.ehr.EhrViews.NewComposition;
import com.doctorexplorer.clinical.ehr.EhrViews.NewPatient;
import com.doctorexplorer.clinical.ehr.EhrViews.PatientDetail;
import com.doctorexplorer.clinical.ehr.EhrViews.PatientView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF08 · pacientes do cofre e notas SOAP. Chega aqui só pela core-api ({@code /api/v1/clinical/**}), já com o
 * {@link Caller} do token interno. Não há rota de exclusão de paciente ou de nota assinada (guarda de 20 anos).
 */
@RestController
public class EhrController {

    private final EhrService ehr;

    public EhrController(EhrService ehr) {
        this.ehr = ehr;
    }

    @GetMapping("/patients")
    Map<String, Object> patients(Caller c, @RequestParam(defaultValue = "") String q) {
        List<PatientView> items = ehr.searchPatients(c, q);
        return Map.of("items", items, "count", items.size());
    }

    @PostMapping("/patients")
    ResponseEntity<PatientView> createPatient(Caller c, @RequestBody NewPatient body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ehr.createPatient(c, body));
    }

    @GetMapping("/patients/{id}")
    PatientDetail patient(Caller c, @PathVariable UUID id) {
        return ehr.patient(c, id);
    }

    @PostMapping("/compositions")
    ResponseEntity<CompositionView> create(Caller c, @RequestBody NewComposition body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ehr.create(c, body));
    }

    @GetMapping("/compositions/{id}")
    CompositionView read(Caller c, @PathVariable UUID id) {
        return ehr.read(c, id);
    }

    /** Rascunho (o editor salva a cada 5 s). */
    @PutMapping("/compositions/{id}")
    CompositionView save(Caller c, @PathVariable UUID id, @RequestBody Draft body) {
        return ehr.saveDraft(c, id, body);
    }

    @PostMapping("/compositions/{id}/sign")
    CompositionView sign(Caller c, @PathVariable UUID id) {
        return ehr.sign(c, id);
    }

    @PostMapping("/compositions/{id}/amend")
    ResponseEntity<CompositionView> amend(Caller c, @PathVariable UUID id, @RequestBody AmendRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ehr.amend(c, id, body.reason()));
    }

    @GetMapping("/compositions/{id}/versions")
    Map<String, Object> versions(Caller c, @PathVariable UUID id) {
        return Map.of("versions", ehr.versions(c, id));
    }

    /** Só rascunho pode ser descartado. */
    @DeleteMapping("/compositions/{id}")
    ResponseEntity<Void> discard(Caller c, @PathVariable UUID id) {
        ehr.discard(c, id);
        return ResponseEntity.noContent().build();
    }
}
