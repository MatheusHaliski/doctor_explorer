package com.doctorexplorer.clinical.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.ehr.EhrService;
import com.doctorexplorer.clinical.ehr.EhrService.Loaded;
import com.doctorexplorer.clinical.ehr.SoapComposition;
import com.doctorexplorer.clinical.terminology.Cid10;
import com.doctorexplorer.clinical.web.ApiProblemException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /fhir/r4}: leitura de Patient, Practitioner, Encounter, Composition, Condition e Provenance do cofre de
 * quem chama, e buscas {@code Composition?patient=}, {@code Condition?encounter=} e {@code Provenance?target=}.
 * Toda leitura passa pela auditoria (clinical.fhir_read).
 */
@RestController
@RequestMapping("/fhir/r4")
public class FhirController {

    static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json;charset=UTF-8");

    private final EhrService ehr;
    private final FhirMapper mapper;
    private final Cid10 cid10;
    private final FhirContext ctx = FhirContext.forR4Cached();
    private final String base;

    public FhirController(EhrService ehr, FhirMapper mapper, Cid10 cid10,
            @Value("${doctor-explorer.fhir.base-url:https://doctorexplorer.app/api/v1/clinical/fhir/r4}") String base) {
        this.ehr = ehr;
        this.mapper = mapper;
        this.cid10 = cid10;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @GetMapping("/metadata")
    ResponseEntity<String> metadata() {
        CapabilityStatement cs = new CapabilityStatement();
        cs.setStatus(Enumerations.PublicationStatus.ACTIVE).setDate(new Date()).setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE)
                .setFhirVersion(Enumerations.FHIRVersion._4_0_1).addFormat("json");
        cs.getImplementation().setDescription("Doctor Explorer · clinical-api (somente leitura)");
        CapabilityStatement.CapabilityStatementRestComponent rest = cs.addRest().setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);
        for (String t : List.of("Patient", "Practitioner", "Encounter", "Composition", "Condition", "Provenance")) {
            rest.addResource().setType(t).addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
        }
        return fhir(cs);
    }

    @GetMapping("/Patient/{id}")
    ResponseEntity<String> patient(Caller c, @PathVariable UUID id) {
        var p = ehr.loadPatient(c, id);
        ehr.auditFhir(c, "Patient", id, id);
        return fhir(mapper.patient(p));
    }

    @GetMapping("/Practitioner/{id}")
    ResponseEntity<String> practitioner(Caller c, @PathVariable UUID id) {
        var p = ehr.loadPractitioner(c, id);
        ehr.auditFhir(c, "Practitioner", id, null);
        return fhir(mapper.practitioner(p));
    }

    @GetMapping("/Encounter/{id}")
    ResponseEntity<String> encounter(Caller c, @PathVariable UUID id) {
        var e = ehr.loadEncounter(c, id);
        ehr.auditFhir(c, "Encounter", id, e.getPatientId());
        return fhir(mapper.encounter(e));
    }

    @GetMapping("/Composition/{id}")
    ResponseEntity<String> composition(Caller c, @PathVariable UUID id) {
        Loaded l = ehr.load(c, id);
        ehr.auditFhir(c, "Composition", id, l.composition().getPatientId());
        return fhir(mapper.composition(l));
    }

    @GetMapping("/Composition")
    ResponseEntity<String> compositionsOf(Caller c, @RequestParam UUID patient) {
        List<Loaded> all = ehr.loadByPatient(c, patient);
        ehr.auditFhir(c, "Composition", null, patient);
        return fhir(searchset(all.stream().map(mapper::composition).map(Resource.class::cast).toList()));
    }

    @GetMapping("/Condition/{id}")
    ResponseEntity<String> condition(Caller c, @PathVariable String id) {
        if (id.length() < 37) throw ApiProblemException.notFound("O diagnóstico");
        Loaded l = ehr.load(c, UUID.fromString(id.substring(0, 36)));
        var cond = mapper.conditions(l, cid10).stream().filter(x -> x.getIdPart().equals(id)).findFirst()
                .orElseThrow(() -> ApiProblemException.notFound("O diagnóstico"));
        ehr.auditFhir(c, "Condition", l.composition().getId(), l.composition().getPatientId());
        return fhir(cond);
    }

    @GetMapping("/Condition")
    ResponseEntity<String> conditionsOf(Caller c, @RequestParam UUID encounter) {
        var e = ehr.loadEncounter(c, encounter);
        List<Resource> out = ehr.loadByPatient(c, e.getPatientId()).stream().filter(l -> l.encounter().getId().equals(encounter))
                .filter(l -> l.composition().getState() == SoapComposition.State.ASSINADA || l.composition().getState() == SoapComposition.State.RASCUNHO)
                .flatMap(l -> mapper.conditions(l, cid10).stream()).map(Resource.class::cast).toList();
        ehr.auditFhir(c, "Condition", encounter, e.getPatientId());
        return fhir(searchset(out));
    }

    @GetMapping("/Provenance")
    ResponseEntity<String> provenance(Caller c, @RequestParam String target) {
        if (!target.startsWith("Composition/")) throw ApiProblemException.notFound("A proveniência");
        Loaded l = ehr.load(c, UUID.fromString(target.substring("Composition/".length())));
        ehr.auditFhir(c, "Provenance", l.composition().getId(), l.composition().getPatientId());
        List<Resource> out = l.composition().getSignedAt() == null ? List.of() : List.of(mapper.provenance(l));
        return fhir(searchset(out));
    }

    private Bundle searchset(List<Resource> resources) {
        Bundle b = new Bundle();
        b.setType(Bundle.BundleType.SEARCHSET);
        b.setTotal(resources.size());
        for (Resource r : resources) b.addEntry().setFullUrl(base + "/" + r.fhirType() + "/" + r.getIdElement().getIdPart()).setResource(r)
                .getSearch().setMode(Bundle.SearchEntryMode.MATCH);
        return b;
    }

    private ResponseEntity<String> fhir(IBaseResource r) {
        return ResponseEntity.ok().contentType(FHIR_JSON).header("Cache-Control", "no-store").body(ctx.newJsonParser().encodeResourceToString(r));
    }
}
