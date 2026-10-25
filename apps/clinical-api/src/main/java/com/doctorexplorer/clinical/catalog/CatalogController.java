package com.doctorexplorer.clinical.catalog;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Busca nos catálogos (sem paciente, sem auditoria). Diz ao web o que é controle especial antes de escolher. */
@RestController
public class CatalogController {

    private final MedicationCatalog medications;
    private final ExamCatalog exams;

    public CatalogController(MedicationCatalog medications, ExamCatalog exams) {
        this.medications = medications;
        this.exams = exams;
    }

    @GetMapping("/catalog/medications")
    Map<String, Object> medications(@RequestParam(defaultValue = "") String q) {
        return Map.of("items", medications.search(q, 25).stream().map(m -> Map.of("ggrem", m.ggrem(), "substance", m.substance(), "product", m.product(),
                "presentation", m.presentation(), "laboratory", m.laboratory(), "tarja", m.tarja().name(), "specialControl", m.specialControl(),
                "antimicrobial", m.antimicrobial())).toList());
    }

    @GetMapping("/catalog/exams")
    Map<String, Object> exams(@RequestParam(defaultValue = "") String q) {
        return Map.of("items", exams.search(q, 40));
    }
}
