package com.doctorexplorer.clinical.terminology;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Autocompletar de CID-10 por código ou palavras da descrição (sem acento). Não envolve paciente: sem auditoria. */
@RestController
public class TerminologyController {

    private final Cid10 cid10;

    public TerminologyController(Cid10 cid10) {
        this.cid10 = cid10;
    }

    @GetMapping("/terminology/cid10")
    Map<String, Object> search(@RequestParam(defaultValue = "") String q) {
        return Map.of("items", cid10.search(q, 20), "system", Cid10.SYSTEM);
    }
}
