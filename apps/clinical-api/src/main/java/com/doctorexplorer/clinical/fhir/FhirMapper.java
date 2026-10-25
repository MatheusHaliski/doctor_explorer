package com.doctorexplorer.clinical.fhir;

import com.doctorexplorer.clinical.catalog.ExamCatalog;
import com.doctorexplorer.clinical.catalog.MedicationCatalog;
import com.doctorexplorer.clinical.documents.ClinicalDocument;
import com.doctorexplorer.clinical.documents.DocumentService;
import com.doctorexplorer.clinical.documents.DocumentViews;
import com.doctorexplorer.clinical.documents.Posology;
import com.doctorexplorer.clinical.ehr.EhrService.Loaded;
import com.doctorexplorer.clinical.ehr.EhrViews.PatientView;
import com.doctorexplorer.clinical.ehr.Encounter;
import com.doctorexplorer.clinical.ehr.Practitioner;
import com.doctorexplorer.clinical.ehr.SoapComposition;
import com.doctorexplorer.clinical.terminology.Cid10;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Signature;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Fachada FHIR R4 (ADR-003/004): o modelo interno (openEHR-like) vira Patient, Practitioner, Encounter,
 * Composition (seções S, O, A, P com códigos LOINC), Condition (CID-10 BR) e Provenance (assinatura).
 */
@Component
public class FhirMapper {

    static final String LOINC = "http://loinc.org";
    static final String CPF = "https://saude.gov.br/fhir/sid/cpf";
    static final String CRM = "https://doctorexplorer.app/fhir/sid/crm";
    static final String RECORD = "https://doctorexplorer.app/fhir/sid/prontuario-local";
    static final String GGREM = "https://www.gov.br/anvisa/cmed/ggrem";

    public Patient patient(PatientView p) {
        Patient r = new Patient();
        r.setId(p.id().toString());
        r.setText(narrative("Paciente " + p.name()));
        if (p.localRecordNumber() != null) r.addIdentifier().setSystem(RECORD).setValue(p.localRecordNumber());
        String name = p.name().trim();
        int last = name.lastIndexOf(' ');
        HumanName hn = r.addName().setUse(HumanName.NameUse.OFFICIAL).setText(name);
        if (last > 0) hn.setFamily(name.substring(last + 1)).addGiven(name.substring(0, last));
        r.setBirthDateElement(new DateType(p.birthDate().toString()));
        r.setGender(switch (p.sex() == null ? "unknown" : p.sex()) {
            case "female" -> Enumerations.AdministrativeGender.FEMALE;
            case "male" -> Enumerations.AdministrativeGender.MALE;
            case "other" -> Enumerations.AdministrativeGender.OTHER;
            default -> Enumerations.AdministrativeGender.UNKNOWN;
        });
        return r;
    }

    public org.hl7.fhir.r4.model.Practitioner practitioner(Practitioner p) {
        org.hl7.fhir.r4.model.Practitioner r = new org.hl7.fhir.r4.model.Practitioner();
        r.setId(p.getId().toString());
        r.setText(narrative("Médico " + p.getName()));
        if (p.getCrmNumber() != null) r.addIdentifier().setSystem(CRM + "/" + p.getCrmUf()).setValue(p.getCrmNumber());
        r.addName().setText(p.getName());
        return r;
    }

    public org.hl7.fhir.r4.model.Encounter encounter(Encounter e) {
        org.hl7.fhir.r4.model.Encounter r = new org.hl7.fhir.r4.model.Encounter();
        r.setId(e.getId().toString());
        r.setText(narrative("Encontro: " + e.getContext()));
        r.setStatus(org.hl7.fhir.r4.model.Encounter.EncounterStatus.FINISHED);
        boolean virtual = e.getContext() == Encounter.Context.TELECONSULTA;
        r.setClass_(new Coding("http://terminology.hl7.org/CodeSystem/v3-ActCode", virtual ? "VR" : "AMB", virtual ? "virtual" : "ambulatory"));
        r.addType().setText(e.getContext().name());
        r.setSubject(new Reference("Patient/" + e.getPatientId()));
        r.addParticipant().setIndividual(new Reference("Practitioner/" + e.getPractitionerId()));
        r.setPeriod(new Period().setStart(Date.from(e.getOccurredAt())));
        return r;
    }

    public Composition composition(Loaded l) {
        SoapComposition s = l.composition();
        Composition r = new Composition();
        r.setId(s.getId().toString());
        r.setText(narrative("Nota SOAP v" + s.getVersion() + " de " + l.patient().name()));
        r.getIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + s.getLineageId());
        r.setStatus(switch (s.getState()) {
            case RASCUNHO -> Composition.CompositionStatus.PRELIMINARY;
            case ASSINADA -> s.getVersion() > 1 ? Composition.CompositionStatus.AMENDED : Composition.CompositionStatus.FINAL;
            case RETIFICADA -> s.getVersion() > 1 ? Composition.CompositionStatus.AMENDED : Composition.CompositionStatus.FINAL;
            case DESCARTADA -> Composition.CompositionStatus.ENTEREDINERROR;
        });
        r.setType(new CodeableConcept(new Coding(LOINC, "11506-3", "Progress note")));
        r.setSubject(new Reference("Patient/" + s.getPatientId()));
        r.setEncounter(new Reference("Encounter/" + s.getEncounterId()));
        r.setDate(Date.from(s.getSignedAt() != null ? s.getSignedAt() : s.getUpdatedAt()));
        r.addAuthor(new Reference("Practitioner/" + s.getAuthorId()));
        r.setTitle("Nota SOAP" + (s.getVersion() > 1 ? " (versão " + s.getVersion() + ")" : ""));
        if (s.getSignedAt() != null) {
            r.addAttester().setMode(Composition.CompositionAttestationMode.LEGAL).setTime(Date.from(s.getSignedAt()))
                    .setParty(new Reference("Practitioner/" + s.getAuthorId()));
        }
        if (s.getPreviousId() != null) {
            r.addRelatesTo().setCode(Composition.DocumentRelationshipType.REPLACES).setTarget(new Reference("Composition/" + s.getPreviousId()));
        }
        section(r, "Subjetivo", "61150-9", "Subjective Narrative", l.content().subjective());
        section(r, "Objetivo", "61149-1", "Objective Narrative", l.content().objective());
        Composition.SectionComponent a = section(r, "Avaliação", "51848-0", "Evaluation note", l.content().assessment());
        for (String code : s.icd10List()) a.addEntry(new Reference("Condition/" + conditionId(s, code)));
        section(r, "Plano", "18776-5", "Plan of care note", l.content().plan());
        return r;
    }

    public List<Condition> conditions(Loaded l, Cid10 cid10) {
        SoapComposition s = l.composition();
        List<Condition> out = new ArrayList<>();
        for (String code : s.icd10List()) {
            Condition c = new Condition();
            c.setId(conditionId(s, code));
            String display = cid10.lookup(code).map(Cid10.Code::display).orElse(code);
            c.setText(narrative(code + " " + display));
            c.setClinicalStatus(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/condition-clinical", "active", "Active")));
            c.setVerificationStatus(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/condition-ver-status", "provisional", "Provisional")));
            c.addCategory(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/condition-category", "encounter-diagnosis", "Encounter Diagnosis")));
            c.setCode(new CodeableConcept(new Coding(Cid10.SYSTEM, code, display)).setText(display));
            c.setSubject(new Reference("Patient/" + s.getPatientId()));
            c.setEncounter(new Reference("Encounter/" + s.getEncounterId()));
            c.setRecorder(new Reference("Practitioner/" + s.getAuthorId()));
            if (s.getSignedAt() != null) c.setRecordedDate(Date.from(s.getSignedAt()));
            out.add(c);
        }
        return out;
    }

    /** Assinatura da nota: quem, quando e o Ed25519 sobre o hash do conteúdo canônico. Só para nota assinada. */
    public Provenance provenance(Loaded l) {
        SoapComposition s = l.composition();
        Provenance p = new Provenance();
        p.setId(s.getId().toString());
        p.setText(narrative("Assinatura da nota SOAP v" + s.getVersion()));
        p.addTarget(new Reference("Composition/" + s.getId()));
        p.setRecorded(Date.from(s.getSignedAt()));
        p.addAgent().setType(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/provenance-participant-type", "author", "Author")))
                .setWho(new Reference("Practitioner/" + s.getAuthorId()));
        Signature sig = p.addSignature();
        sig.addType(new Coding("urn:iso-astm:E1762-95:2013", "1.2.840.10065.1.12.1.1", "Author's Signature"));
        sig.setWhen(Date.from(s.getSignedAt()));
        sig.setWho(new Reference("Practitioner/" + s.getAuthorId()));
        sig.setSigFormat("application/octet-stream");
        sig.setData(Base64.getDecoder().decode(s.getSignature()));
        return p;
    }

    /** RF09 · cada medicamento da receita vira um MedicationRequest; os da mesma receita dividem o groupIdentifier. */
    public List<org.hl7.fhir.r4.model.MedicationRequest> medicationRequests(DocumentService.Loaded l, MedicationCatalog catalog) {
        ClinicalDocument d = l.document();
        List<org.hl7.fhir.r4.model.MedicationRequest> out = new ArrayList<>();
        int n = 0;
        for (DocumentViews.MedicationInput m : l.content().medications()) {
            n++;
            var med = catalog.byGgrem(m.ggrem());
            org.hl7.fhir.r4.model.MedicationRequest r = new org.hl7.fhir.r4.model.MedicationRequest();
            r.setId(d.getId() + "-m" + n);
            String posology = Posology.text(m);
            r.setText(narrative(med.map(MedicationCatalog.Medication::label).orElse(m.ggrem()) + " — " + posology));
            r.setStatus(switch (d.getState()) {
                case ASSINADA, ENVIADA -> org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus.ACTIVE;
                case CANCELADA -> org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus.CANCELLED;
                default -> org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus.DRAFT;
            });
            r.setIntent(org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent.ORDER);
            r.getGroupIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + d.getId());
            r.setMedication(new CodeableConcept(new Coding(GGREM, m.ggrem(), med.map(MedicationCatalog.Medication::label).orElse(null)))
                    .setText(med.map(x -> x.substance() + " · " + x.presentation()).orElse(m.ggrem())));
            r.setSubject(new Reference("Patient/" + d.getPatientId()));
            r.setEncounter(new Reference("Encounter/" + d.getEncounterId()));
            r.setAuthoredOn(Date.from(d.getSignedAt() != null ? d.getSignedAt() : d.getCreatedAt()));
            r.setRequester(new Reference("Practitioner/" + d.getAuthorId()));
            r.addDosageInstruction(dosage(m, posology));
            if (d.getValidUntil() != null) {
                r.getDispenseRequest().setValidityPeriod(new Period().setEnd(Date.from(d.getValidUntil().atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant())));
            }
            out.add(r);
        }
        return out;
    }

    /** RF09 · cada exame do pedido vira um ServiceRequest com LOINC e o código TUSS equivalente. */
    public List<org.hl7.fhir.r4.model.ServiceRequest> serviceRequests(DocumentService.Loaded l, ExamCatalog catalog) {
        ClinicalDocument d = l.document();
        List<org.hl7.fhir.r4.model.ServiceRequest> out = new ArrayList<>();
        int n = 0;
        for (DocumentViews.ExamInput e : l.content().exams()) {
            n++;
            var ex = catalog.byLoinc(e.loinc()).orElseThrow();
            org.hl7.fhir.r4.model.ServiceRequest r = new org.hl7.fhir.r4.model.ServiceRequest();
            r.setId(d.getId() + "-e" + n);
            r.setText(narrative(ex.name() + " (TUSS " + ex.tuss() + ")"));
            r.setStatus(switch (d.getState()) {
                case ASSINADA, ENVIADA -> org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus.ACTIVE;
                case CANCELADA -> org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus.REVOKED;
                default -> org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus.DRAFT;
            });
            r.setIntent(org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestIntent.ORDER);
            r.getRequisition().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + d.getId());
            CodeableConcept code = new CodeableConcept().setText(ex.name());
            code.addCoding(new Coding(ExamCatalog.LOINC, ex.loinc(), null));
            code.addCoding(new Coding(ExamCatalog.TUSS, ex.tuss(), ex.tussTerm()));
            r.setCode(code);
            r.setSubject(new Reference("Patient/" + d.getPatientId()));
            r.setEncounter(new Reference("Encounter/" + d.getEncounterId()));
            r.setAuthoredOn(Date.from(d.getSignedAt() != null ? d.getSignedAt() : d.getCreatedAt()));
            r.setRequester(new Reference("Practitioner/" + d.getAuthorId()));
            if (!l.content().indication().isBlank()) r.addReasonCode().setText(l.content().indication());
            out.add(r);
        }
        return out;
    }

    static org.hl7.fhir.r4.model.Dosage dosage(DocumentViews.MedicationInput m, String text) {
        org.hl7.fhir.r4.model.Dosage d = new org.hl7.fhir.r4.model.Dosage().setText(text);
        org.hl7.fhir.r4.model.Timing.TimingRepeatComponent rep = d.getTiming().getRepeat();
        if (m.intervalHours() != null) {
            rep.setFrequency(1).setPeriod(m.intervalHours()).setPeriodUnit(org.hl7.fhir.r4.model.Timing.UnitsOfTime.H);
        } else if (m.timesPerDay() != null) {
            rep.setFrequency(m.timesPerDay()).setPeriod(1).setPeriodUnit(org.hl7.fhir.r4.model.Timing.UnitsOfTime.D);
        }
        if (m.durationDays() != null) {
            rep.setBounds(new org.hl7.fhir.r4.model.Duration().setValue(m.durationDays()).setUnit("d").setSystem("http://unitsofmeasure.org").setCode("d"));
        }
        d.setRoute(new CodeableConcept().setText(Posology.ROUTES.getOrDefault(m.route(), m.route())));
        java.math.BigDecimal value = doseValue(m.dose());
        if (value != null) d.addDoseAndRate().setDose(new org.hl7.fhir.r4.model.Quantity().setValue(value).setUnit(m.doseUnit()));
        return d;
    }

    static java.math.BigDecimal doseValue(String dose) {
        if (dose.matches("\\d/\\d")) return java.math.BigDecimal.valueOf(Integer.parseInt(dose.substring(0, 1))).divide(java.math.BigDecimal.valueOf(Integer.parseInt(dose.substring(2))), 3, java.math.RoundingMode.HALF_UP);
        try {
            return new java.math.BigDecimal(dose.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String conditionId(SoapComposition s, String code) {
        return s.getId() + "-" + code.replace(".", "");
    }

    private static Composition.SectionComponent section(Composition r, String title, String loinc, String display, String text) {
        Composition.SectionComponent sc = r.addSection().setTitle(title).setCode(new CodeableConcept(new Coding(LOINC, loinc, display)));
        sc.setText(narrative(text == null || text.isBlank() ? "(vazio)" : text));
        return sc;
    }

    private static Narrative narrative(String text) {
        Narrative n = new Narrative();
        n.setStatus(Narrative.NarrativeStatus.GENERATED);
        n.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>" + HtmlUtils.htmlEscape(text).replace("\n", "<br/>") + "</p></div>");
        return n;
    }
}
