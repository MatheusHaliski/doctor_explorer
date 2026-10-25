package com.doctorexplorer.clinical.ehr;

import com.doctorexplorer.clinical.audit.CoreAudit;
import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.common.UuidV7;
import com.doctorexplorer.clinical.crypto.BlindIndex;
import com.doctorexplorer.clinical.crypto.NoteSigner;
import com.doctorexplorer.clinical.crypto.VaultCrypto;
import com.doctorexplorer.clinical.ehr.EhrViews.AssessmentView;
import com.doctorexplorer.clinical.ehr.EhrViews.AuthorView;
import com.doctorexplorer.clinical.ehr.EhrViews.CompositionSummary;
import com.doctorexplorer.clinical.ehr.EhrViews.CompositionView;
import com.doctorexplorer.clinical.ehr.EhrViews.Draft;
import com.doctorexplorer.clinical.ehr.EhrViews.EncounterView;
import com.doctorexplorer.clinical.ehr.EhrViews.Integrity;
import com.doctorexplorer.clinical.ehr.EhrViews.Link;
import com.doctorexplorer.clinical.ehr.EhrViews.NewComposition;
import com.doctorexplorer.clinical.ehr.EhrViews.NewPatient;
import com.doctorexplorer.clinical.ehr.EhrViews.PatientDetail;
import com.doctorexplorer.clinical.ehr.EhrViews.PatientView;
import com.doctorexplorer.clinical.terminology.Cid10;
import com.doctorexplorer.clinical.web.ApiProblemException;
import com.doctorexplorer.clinical.web.ValidationProblemException;
import com.doctorexplorer.clinical.web.ValidationProblemException.FieldProblem;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * RF08 · prontuário SOAP no cofre do médico. Tudo aqui passa por {@link #open}: garante o cofre, atualiza o
 * retrato do médico e desembrulha a chave de dados. Cada leitura e escrita registra auditoria antes de responder
 * (critério 7); escrita e auditoria vivem na mesma transação.
 */
@Service
public class EhrService {

    static final int MAX_TEXT = 20_000;
    static final Set<String> LINK_TYPES = Set.of("POST", "CONVERSATION", "CALL");
    static final Set<String> SEXES = Set.of("female", "male", "other", "unknown");

    private final ClinicalVaults vaults;
    private final Practitioners practitioners;
    private final Patients patients;
    private final PatientNameTokens nameTokens;
    private final Encounters encounters;
    private final SoapCompositions compositions;
    private final VaultCrypto crypto;
    private final BlindIndex index;
    private final NoteSigner signer;
    private final Cid10 cid10;
    private final CoreAudit audit;
    private final ObjectMapper json = new ObjectMapper();

    public EhrService(ClinicalVaults vaults, Practitioners practitioners, Patients patients, PatientNameTokens nameTokens, Encounters encounters,
            SoapCompositions compositions, VaultCrypto crypto, BlindIndex index, NoteSigner signer, Cid10 cid10, CoreAudit audit) {
        this.vaults = vaults;
        this.practitioners = practitioners;
        this.patients = patients;
        this.nameTokens = nameTokens;
        this.encounters = encounters;
        this.compositions = compositions;
        this.crypto = crypto;
        this.index = index;
        this.signer = signer;
        this.cid10 = cid10;
        this.audit = audit;
    }

    /** Cofre aberto para a requisição: entidade + chave de dados desembrulhada (só em memória). */
    public record Open(ClinicalVault vault, byte[] dek, Practitioner me) {}

    Open open(Caller c) {
        practitioners.upsert(c.userId(), c.name() == null || c.name().isBlank() ? "Médico" : c.name(), c.crm(), c.uf());
        ClinicalVault v = vaults.findByOwnerTypeAndOwnerId(ClinicalVault.OwnerType.PRACTITIONER, c.userId()).orElseGet(() -> {
            UUID id = ClinicalVault.newId();
            vaults.createIfAbsent(id, ClinicalVault.OwnerType.PRACTITIONER.name(), c.userId(), crypto.newWrappedDek(id), VaultCrypto.KEY_ID);
            return vaults.findByOwnerTypeAndOwnerId(ClinicalVault.OwnerType.PRACTITIONER, c.userId()).orElseThrow();
        });
        return new Open(v, crypto.unwrapDek(v.getId(), v.getWrappedDek()), practitioners.findById(c.userId()).orElseThrow());
    }

    // ---------- pacientes ----------

    @Transactional
    public List<PatientView> searchPatients(Caller c, String q) {
        Open o = open(c);
        List<Patient> found;
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            found = patients.findTop20ByVaultIdOrderByLastRecordAtDesc(o.vault().getId());
        } else {
            List<String> tokens = index.queryTokens(o.vault().getId(), query);
            if (tokens.isEmpty()) throw new ValidationProblemException(List.of(new FieldProblem("q", "Digite ao menos 2 letras do nome.")));
            found = patients.searchByTokens(o.vault().getId(), tokens, tokens.size());
        }
        // o termo buscado não vai para a auditoria: só a contagem
        audit.record(c, query.isEmpty() ? "clinical.patient_list" : "clinical.patient_search", "patient", null, null, Map.of("results", found.size()));
        return found.stream().map(p -> patientView(o, p)).toList();
    }

    @Transactional
    public PatientView createPatient(Caller c, NewPatient n) {
        List<FieldProblem> problems = new ArrayList<>();
        String name = n.name() == null ? "" : n.name().trim().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 200) problems.add(new FieldProblem("name", "Informe o nome completo (3 a 200 caracteres)."));
        LocalDate birth = null;
        try {
            birth = n.birthDate() == null ? null : LocalDate.parse(n.birthDate());
        } catch (DateTimeParseException e) {
            // tratado abaixo
        }
        if (birth == null || birth.isAfter(LocalDate.now()) || birth.isBefore(LocalDate.of(1900, 1, 1))) {
            problems.add(new FieldProblem("birthDate", "Informe a data de nascimento (AAAA-MM-DD), entre 1900 e hoje."));
        }
        String cpf = n.cpf() == null ? "" : n.cpf().replaceAll("\\D", "");
        if (!cpf.isEmpty() && !validCpf(cpf)) problems.add(new FieldProblem("cpf", "CPF inválido. Confira os dígitos ou deixe em branco."));
        if (n.sex() != null && !n.sex().isBlank() && !SEXES.contains(n.sex())) problems.add(new FieldProblem("sex", "Use female, male, other ou unknown."));
        if (n.localRecordNumber() != null && n.localRecordNumber().length() > 40) problems.add(new FieldProblem("localRecordNumber", "Até 40 caracteres."));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        Open o = open(c);
        UUID id = UuidV7.generate();
        Patient p = patients.save(new Patient(id, o.vault().getId(), crypto.encrypt(o.dek(), "patient.name", id, name),
                crypto.encrypt(o.dek(), "patient.birth_date", id, birth.toString()), cpf.isEmpty() ? null : crypto.encrypt(o.dek(), "patient.cpf", id, cpf),
                n.sex() == null || n.sex().isBlank() ? null : n.sex(), blankToNull(n.localRecordNumber()), c.userId()));
        index.tokensFor(o.vault().getId(), name).forEach(t -> nameTokens.save(new PatientNameToken(id, t)));
        audit.record(c, "clinical.patient_create", "patient", id, id, Map.of());
        return patientView(o, p);
    }

    @Transactional
    public PatientDetail patient(Caller c, UUID id) {
        Open o = open(c);
        Patient p = ownedPatient(o, id);
        List<CompositionSummary> list = compositions.findByPatientIdAndStateNotOrderByCreatedAtDesc(id, SoapComposition.State.DESCARTADA).stream()
                .map(s -> {
                    Encounter e = encounters.findById(s.getEncounterId()).orElseThrow();
                    return new CompositionSummary(s.getId(), s.getLineageId(), s.getVersion(), s.getState().name(), e.getContext().name(), e.getOccurredAt(),
                            s.icd10List(), s.getSignedAt(), s.getUpdatedAt(), s.getSupersededBy());
                }).toList();
        audit.record(c, "clinical.patient_read", "patient", id, id, Map.of("compositions", list.size()));
        return new PatientDetail(patientView(o, p), list);
    }

    // ---------- notas SOAP ----------

    @Transactional
    public CompositionView create(Caller c, NewComposition n) {
        Open o = open(c);
        if (n.patientId() == null) throw new ValidationProblemException(List.of(new FieldProblem("patientId", "Escolha o paciente do cofre.")));
        Patient p = ownedPatient(o, n.patientId());
        List<Link> links = validLinks(n.links(), new ArrayList<>(), true);
        Encounter.Context ctx;
        try {
            ctx = n.context() == null || n.context().isBlank()
                    ? (links.stream().anyMatch(l -> l.type().equals("POST")) ? Encounter.Context.DISCUSSAO_DE_CASO : Encounter.Context.CONSULTA)
                    : Encounter.Context.valueOf(n.context());
        } catch (IllegalArgumentException e) {
            throw new ValidationProblemException(List.of(new FieldProblem("context", "Use CONSULTA, DISCUSSAO_DE_CASO, TELECONSULTA ou INTERCONSULTA.")));
        }
        Instant at;
        try {
            at = n.occurredAt() == null || n.occurredAt().isBlank() ? Instant.now() : Instant.parse(n.occurredAt());
        } catch (DateTimeParseException e) {
            throw new ValidationProblemException(List.of(new FieldProblem("occurredAt", "Data e hora em ISO-8601 (ex.: 2026-10-25T14:00:00Z).")));
        }
        if (at.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            throw new ValidationProblemException(List.of(new FieldProblem("occurredAt", "O encontro não pode estar no futuro.")));
        }
        Encounter e = encounters.save(new Encounter(UuidV7.generate(), o.vault().getId(), p.getId(), c.userId(), ctx, at));
        UUID id = UuidV7.generate();
        SoapComposition s = new SoapComposition(id, o.vault().getId(), e.getId(), p.getId(), c.userId(), id, 1, null, null);
        s.saveDraft(null, null, null, null, List.of(), List.of(), linksJson(links));
        compositions.save(s);
        audit.record(c, "clinical.composition_create", "composition", id, p.getId(), Map.of("context", ctx, "links", links.size()));
        return view(o, s, c);
    }

    @Transactional
    public CompositionView saveDraft(Caller c, UUID id, Draft d) {
        Open o = open(c);
        SoapComposition s = ownedComposition(o, id);
        requireAuthor(c, s);
        if (s.getState() != SoapComposition.State.RASCUNHO) throw immutable();
        List<FieldProblem> problems = new ArrayList<>();
        checkText(problems, "subjective", d.subjective());
        checkText(problems, "objective", d.objective());
        checkText(problems, "assessment.text", d.assessment() == null ? null : d.assessment().text());
        checkText(problems, "plan", d.plan());
        List<String> icd = new ArrayList<>();
        for (String raw : d.assessment() == null || d.assessment().icd10() == null ? List.<String>of() : d.assessment().icd10()) {
            cid10.lookup(raw).ifPresentOrElse(code -> { if (!icd.contains(code.code())) icd.add(code.code()); },
                    () -> problems.add(new FieldProblem("assessment.icd10", "Código CID-10 desconhecido: " + raw + ".")));
        }
        List<String> snomed = new ArrayList<>();
        for (String raw : d.assessment() == null || d.assessment().snomed() == null ? List.<String>of() : d.assessment().snomed()) {
            if (raw == null || !raw.trim().matches("\\d{6,18}")) problems.add(new FieldProblem("assessment.snomed", "Código SNOMED CT tem de 6 a 18 dígitos: " + raw + "."));
            else if (!snomed.contains(raw.trim())) snomed.add(raw.trim());
        }
        List<Link> links = d.links() == null ? readLinks(s.getLinks()) : validLinks(d.links(), problems, false);
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        s.saveDraft(crypto.encrypt(o.dek(), "soap.subjective", id, nz(d.subjective())), crypto.encrypt(o.dek(), "soap.objective", id, nz(d.objective())),
                crypto.encrypt(o.dek(), "soap.assessment", id, d.assessment() == null ? "" : nz(d.assessment().text())),
                crypto.encrypt(o.dek(), "soap.plan", id, nz(d.plan())), icd, snomed, linksJson(links));
        compositions.save(s);
        audit.record(c, "clinical.composition_draft", "composition", id, s.getPatientId(), Map.of("version", s.getVersion()));
        return view(o, s, c);
    }

    @Transactional
    public CompositionView read(Caller c, UUID id) {
        Open o = open(c);
        SoapComposition s = ownedComposition(o, id);
        CompositionView v = view(o, s, c);
        audit.record(c, "clinical.composition_read", "composition", id, s.getPatientId(), Map.of("version", s.getVersion(), "state", s.getState()));
        return v;
    }

    @Transactional
    public List<CompositionView> versions(Caller c, UUID id) {
        Open o = open(c);
        SoapComposition s = ownedComposition(o, id);
        List<CompositionView> all = compositions.findByLineageIdOrderByVersionAsc(s.getLineageId()).stream()
                .filter(x -> x.getState() != SoapComposition.State.DESCARTADA).map(x -> view(o, x, c)).toList();
        audit.record(c, "clinical.composition_versions", "composition", s.getLineageId(), s.getPatientId(), Map.of("versions", all.size()));
        return all;
    }

    /** Assinatura: sudo recente + conteúdo completo; carimbo do servidor e hash canônico assinado com Ed25519. */
    @Transactional
    public CompositionView sign(Caller c, UUID id) {
        if (!c.sudoActive()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sudo-necessario", "Confirme sua identidade",
                    "Para assinar, confirme sua senha (e o código do autenticador, se o 2FA estiver ativo) em POST /api/v1/me/sudo.");
        }
        Open o = open(c);
        SoapComposition s = ownedComposition(o, id);
        requireAuthor(c, s);
        if (s.getState() != SoapComposition.State.RASCUNHO) throw immutable();
        Content content = decrypt(o, s);
        List<FieldProblem> problems = new ArrayList<>();
        if (content.subjective().isBlank()) problems.add(new FieldProblem("subjective", "Preencha o Subjetivo antes de assinar."));
        if (content.objective().isBlank()) problems.add(new FieldProblem("objective", "Preencha o Objetivo antes de assinar."));
        if (s.icd10List().isEmpty()) problems.add(new FieldProblem("assessment.icd10", "A Avaliação precisa de ao menos um CID-10."));
        if (content.plan().isBlank()) problems.add(new FieldProblem("plan", "Preencha o Plano antes de assinar."));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        SoapComposition previous = s.getPreviousId() == null ? null : compositions.findById(s.getPreviousId()).orElseThrow();
        if (previous != null && (previous.getState() != SoapComposition.State.ASSINADA || previous.getSupersededBy() != null)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "versao-desatualizada", "Versão desatualizada",
                    "Outra versão desta nota já foi assinada. Abra a versão atual para corrigir.");
        }
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String hash = contentHash(s, content, previous == null ? "" : previous.getContentHash(), at);
        s.sign(hash, signer.sign(hash), signer.keyId(), at);
        compositions.save(s);
        if (previous != null) {
            previous.supersededBy(s.getId());
            compositions.save(previous);
        }
        Patient p = patients.findById(s.getPatientId()).orElseThrow();
        p.touched();
        patients.save(p);
        audit.record(c, "clinical.composition_sign", "composition", id, s.getPatientId(), Map.of("version", s.getVersion(), "hash", hash));
        return view(o, s, c);
    }

    /** Retificação: nova versão em rascunho com o conteúdo da atual e o motivo; a assinada continua visível. */
    @Transactional
    public CompositionView amend(Caller c, UUID id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 10) throw new ValidationProblemException(List.of(new FieldProblem("reason", "Explique o motivo da correção (pelo menos 10 caracteres).")));
        Open o = open(c);
        SoapComposition s = ownedComposition(o, id);
        requireAuthor(c, s);
        if (s.getState() != SoapComposition.State.ASSINADA || s.getSupersededBy() != null) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "nao-retificavel", "Só a versão assinada atual é retificada",
                    s.getState() == SoapComposition.State.RASCUNHO ? "Esta versão ainda é rascunho: edite e assine." : "Abra a versão mais recente desta nota.");
        }
        if (compositions.existsByLineageIdAndState(s.getLineageId(), SoapComposition.State.RASCUNHO)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "retificacao-aberta", "Já existe uma retificação em rascunho", "Continue a retificação aberta.");
        }
        Content content = decrypt(o, s);
        UUID nid = UuidV7.generate();
        SoapComposition next = new SoapComposition(nid, s.getVaultId(), s.getEncounterId(), s.getPatientId(), c.userId(), s.getLineageId(), s.getVersion() + 1,
                s.getId(), why);
        next.saveDraft(crypto.encrypt(o.dek(), "soap.subjective", nid, content.subjective()), crypto.encrypt(o.dek(), "soap.objective", nid, content.objective()),
                crypto.encrypt(o.dek(), "soap.assessment", nid, content.assessment()), crypto.encrypt(o.dek(), "soap.plan", nid, content.plan()),
                s.icd10List(), s.snomedList(), s.getLinks());
        compositions.save(next);
        audit.record(c, "clinical.composition_amend", "composition", nid, s.getPatientId(), Map.of("from", s.getId(), "version", next.getVersion()));
        return view(o, next, c);
    }

    @Transactional
    public void discard(Caller c, UUID id) {
        Open o = open(c);
        SoapComposition s = ownedComposition(o, id);
        requireAuthor(c, s);
        if (s.getState() != SoapComposition.State.RASCUNHO) throw immutable();
        s.discard();
        compositions.save(s);
        audit.record(c, "clinical.composition_discard", "composition", id, s.getPatientId(), Map.of("version", s.getVersion()));
    }

    // ---------- leitura para a fachada FHIR ----------

    /** Nota decifrada com o encontro, o paciente e o autor, para a fachada FHIR (a auditoria fica com quem chama). */
    public record Loaded(SoapComposition composition, Content content, Encounter encounter, PatientView patient, Practitioner author) {}

    public record Content(String subjective, String objective, String assessment, String plan) {}

    @Transactional
    public Loaded load(Caller c, UUID compositionId) {
        Open o = open(c);
        SoapComposition s = ownedComposition(o, compositionId);
        return loaded(o, s);
    }

    @Transactional
    public List<Loaded> loadByPatient(Caller c, UUID patientId) {
        Open o = open(c);
        ownedPatient(o, patientId);
        return compositions.findByPatientIdAndStateNotOrderByCreatedAtDesc(patientId, SoapComposition.State.DESCARTADA).stream().map(s -> loaded(o, s)).toList();
    }

    @Transactional
    public PatientView loadPatient(Caller c, UUID patientId) {
        Open o = open(c);
        return patientView(o, ownedPatient(o, patientId));
    }

    @Transactional
    public Encounter loadEncounter(Caller c, UUID encounterId) {
        Open o = open(c);
        return encounters.findById(encounterId).filter(e -> e.getVaultId().equals(o.vault().getId())).orElseThrow(() -> ApiProblemException.notFound("O encontro"));
    }

    @Transactional
    public Practitioner loadPractitioner(Caller c, UUID practitionerId) {
        open(c);
        // só o próprio médico no MVP (cofre pessoal); colegas entram com o cofre da instituição
        if (!practitionerId.equals(c.userId())) throw ApiProblemException.notFound("O profissional");
        return practitioners.findById(practitionerId).orElseThrow(() -> ApiProblemException.notFound("O profissional"));
    }

    public void auditFhir(Caller c, String resourceType, UUID resourceId, UUID patientId) {
        audit.record(c, "clinical.fhir_read", resourceType, resourceId, patientId, Map.of());
    }

    // ---------- apoio ----------

    private Loaded loaded(Open o, SoapComposition s) {
        Encounter e = encounters.findById(s.getEncounterId()).orElseThrow();
        Patient p = patients.findById(s.getPatientId()).orElseThrow();
        Practitioner author = practitioners.findById(s.getAuthorId()).orElseThrow();
        return new Loaded(s, decrypt(o, s), e, patientView(o, p), author);
    }

    private Patient ownedPatient(Open o, UUID id) {
        return patients.findById(id).filter(p -> p.getVaultId().equals(o.vault().getId())).orElseThrow(() -> ApiProblemException.notFound("O paciente"));
    }

    private SoapComposition ownedComposition(Open o, UUID id) {
        return compositions.findById(id).filter(s -> s.getVaultId().equals(o.vault().getId()) && s.getState() != SoapComposition.State.DESCARTADA)
                .orElseThrow(() -> ApiProblemException.notFound("A nota"));
    }

    private static void requireAuthor(Caller c, SoapComposition s) {
        if (!s.getAuthorId().equals(c.userId())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "nao-autor", "Só o autor altera a nota", "Peça ao autor ou crie uma nota sua.");
        }
    }

    private static ApiProblemException immutable() {
        return new ApiProblemException(HttpStatus.CONFLICT, "nota-imutavel", "Nota assinada é imutável",
                "A nota assinada não pode ser alterada. Crie uma nova versão com o motivo da correção.");
    }

    private Content decrypt(Open o, SoapComposition s) {
        UUID id = s.getId();
        return new Content(nz(crypto.decrypt(o.dek(), "soap.subjective", id, s.getSubjectiveEnc())), nz(crypto.decrypt(o.dek(), "soap.objective", id, s.getObjectiveEnc())),
                nz(crypto.decrypt(o.dek(), "soap.assessment", id, s.getAssessmentEnc())), nz(crypto.decrypt(o.dek(), "soap.plan", id, s.getPlanEnc())));
    }

    /** SHA-256 do JSON canônico (ordem fixa de campos, listas ordenadas) com o hash da versão anterior encadeado. */
    String contentHash(SoapComposition s, Content content, String previousHash, Instant signedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("compositionId", s.getId().toString());
        m.put("lineageId", s.getLineageId().toString());
        m.put("version", s.getVersion());
        m.put("patientId", s.getPatientId().toString());
        m.put("encounterId", s.getEncounterId().toString());
        m.put("authorId", s.getAuthorId().toString());
        m.put("subjective", content.subjective());
        m.put("objective", content.objective());
        m.put("assessment", content.assessment());
        m.put("icd10", s.icd10List().stream().sorted().toList());
        m.put("snomed", s.snomedList().stream().sorted().toList());
        m.put("plan", content.plan());
        m.put("links", readLinks(s.getLinks()).stream().sorted(Comparator.comparing(Link::type).thenComparing(Link::ref))
                .map(l -> List.of(l.type(), l.ref())).toList());
        m.put("amendReason", nz(s.getAmendReason()));
        m.put("previousHash", nz(previousHash));
        m.put("signedAt", signedAt.toString());
        return NoteSigner.sha256Hex(json.writeValueAsString(m).getBytes(StandardCharsets.UTF_8));
    }

    private Integrity integrity(Open o, SoapComposition s) {
        if (s.getContentHash() == null) return null;
        String previousHash = s.getPreviousId() == null ? "" : compositions.findById(s.getPreviousId()).map(SoapComposition::getContentHash).orElse("");
        boolean ok = contentHash(s, decrypt(o, s), previousHash, s.getSignedAt()).equals(s.getContentHash()) && signer.verify(s.getContentHash(), s.getSignature());
        return new Integrity(ok, "SHA-256 + Ed25519");
    }

    private CompositionView view(Open o, SoapComposition s, Caller c) {
        Content content = decrypt(o, s);
        Encounter e = encounters.findById(s.getEncounterId()).orElseThrow();
        Patient p = patients.findById(s.getPatientId()).orElseThrow();
        Practitioner a = practitioners.findById(s.getAuthorId()).orElseThrow();
        boolean author = s.getAuthorId().equals(c.userId());
        return new CompositionView(s.getId(), s.getLineageId(), s.getVersion(), s.getState().name(), p.getId(),
                crypto.decrypt(o.dek(), "patient.name", p.getId(), p.getNameEnc()), new EncounterView(e.getId(), e.getContext().name(), e.getOccurredAt()),
                new AuthorView(a.getId(), a.getName(), a.getCrmNumber(), a.getCrmUf()), content.subjective(), content.objective(),
                new AssessmentView(content.assessment(), s.icd10List().stream().map(code -> cid10.lookup(code).orElse(new Cid10.Code(code, ""))).toList(), s.snomedList()),
                content.plan(), readLinks(s.getLinks()), s.getAmendReason(), s.getPreviousId(), s.getSupersededBy(), s.getSignedAt(), s.getContentHash(),
                s.getSignature(), s.getSigningKeyId(), integrity(o, s), s.getCreatedAt(), s.getUpdatedAt(),
                author && s.getState() == SoapComposition.State.RASCUNHO, author && s.getState() == SoapComposition.State.ASSINADA && s.getSupersededBy() == null);
    }

    private PatientView patientView(Open o, Patient p) {
        String cpf = crypto.decrypt(o.dek(), "patient.cpf", p.getId(), p.getCpfEnc());
        return new PatientView(p.getId(), crypto.decrypt(o.dek(), "patient.name", p.getId(), p.getNameEnc()),
                LocalDate.parse(crypto.decrypt(o.dek(), "patient.birth_date", p.getId(), p.getBirthDateEnc())),
                cpf == null ? null : "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**", p.getSex(), p.getLocalRecordNumber(), p.getLastRecordAt());
    }

    private List<Link> validLinks(List<Link> in, List<FieldProblem> problems, boolean throwNow) {
        List<Link> out = new ArrayList<>();
        List<FieldProblem> local = new ArrayList<>();
        if (in != null) {
            if (in.size() > 10) local.add(new FieldProblem("links", "No máximo 10 vínculos por nota."));
            for (Link l : in) {
                if (l == null || l.type() == null || !LINK_TYPES.contains(l.type())) {
                    local.add(new FieldProblem("links", "Vínculo precisa ser POST, CONVERSATION ou CALL."));
                    continue;
                }
                try {
                    out.add(new Link(l.type(), UUID.fromString(l.ref()).toString()));
                } catch (RuntimeException e) {
                    local.add(new FieldProblem("links", "O vínculo usa o id opaco da publicação, conversa ou chamada."));
                }
            }
        }
        problems.addAll(local);
        if (throwNow && !local.isEmpty()) throw new ValidationProblemException(local);
        return out;
    }

    private String linksJson(List<Link> links) {
        return json.writeValueAsString(links.stream().map(l -> Map.of("type", l.type(), "ref", l.ref())).toList());
    }

    List<Link> readLinks(String raw) {
        List<Map<String, String>> list = json.readValue(raw == null || raw.isBlank() ? "[]" : raw, new TypeReference<List<Map<String, String>>>() {});
        return list.stream().map(m -> new Link(m.get("type"), m.get("ref"))).toList();
    }

    private static void checkText(List<FieldProblem> problems, String field, String v) {
        if (v != null && v.length() > MAX_TEXT) problems.add(new FieldProblem(field, "Até " + MAX_TEXT + " caracteres."));
    }

    static boolean validCpf(String d) {
        if (d.length() != 11 || d.chars().distinct().count() == 1) return false;
        for (int n = 9; n <= 10; n++) {
            int sum = 0;
            for (int i = 0; i < n; i++) sum += (d.charAt(i) - '0') * (n + 1 - i);
            int dv = (sum * 10) % 11 % 10;
            if (dv != d.charAt(n) - '0') return false;
        }
        return true;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
