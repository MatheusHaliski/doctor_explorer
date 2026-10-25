package com.doctorexplorer.clinical.documents;

import com.doctorexplorer.clinical.audit.CoreAudit;
import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.catalog.ExamCatalog;
import com.doctorexplorer.clinical.catalog.MedicationCatalog;
import com.doctorexplorer.clinical.common.UuidV7;
import com.doctorexplorer.clinical.crypto.NoteSigner;
import com.doctorexplorer.clinical.crypto.VaultCrypto;
import com.doctorexplorer.clinical.documents.DocumentViews.Content;
import com.doctorexplorer.clinical.documents.DocumentViews.Delivered;
import com.doctorexplorer.clinical.documents.DocumentViews.DeliveryView;
import com.doctorexplorer.clinical.documents.DocumentViews.DocumentSummary;
import com.doctorexplorer.clinical.documents.DocumentViews.DocumentView;
import com.doctorexplorer.clinical.documents.DocumentViews.ExamInput;
import com.doctorexplorer.clinical.documents.DocumentViews.ExamView;
import com.doctorexplorer.clinical.documents.DocumentViews.MedicationInput;
import com.doctorexplorer.clinical.documents.DocumentViews.MedicationView;
import com.doctorexplorer.clinical.documents.DocumentViews.PublicView;
import com.doctorexplorer.clinical.ehr.ClinicalVaults;
import com.doctorexplorer.clinical.ehr.EhrService;
import com.doctorexplorer.clinical.ehr.EhrService.Open;
import com.doctorexplorer.clinical.ehr.EhrViews.PatientView;
import com.doctorexplorer.clinical.ehr.Encounter;
import com.doctorexplorer.clinical.ehr.Practitioner;
import com.doctorexplorer.clinical.ehr.Practitioners;
import com.doctorexplorer.clinical.signature.PadesSigner;
import com.doctorexplorer.clinical.signature.PadesVerifier;
import com.doctorexplorer.clinical.signature.SignatureFailure;
import com.doctorexplorer.clinical.signature.SignatureProviderPort;
import com.doctorexplorer.clinical.signature.SignerIdentity;
import com.doctorexplorer.clinical.web.ApiProblemException;
import com.doctorexplorer.clinical.web.ValidationProblemException;
import com.doctorexplorer.clinical.web.ValidationProblemException.FieldProblem;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * RF09 · receita simples e pedido de exame. Fluxo com estado por etapa (critério 7): rascunho → (revisão no web,
 * confirmada pelo hash do conteúdo) → assinando no provedor → assinada → enviada; falha volta para nova tentativa;
 * cancelada com motivo nunca some (o validador mostra "cancelada"). Toda leitura e escrita é auditada.
 */
@Service
public class DocumentService {

    static final Duration DELIVERY_TTL = Duration.ofDays(7);
    static final Duration STUCK_SIGNING = Duration.ofMinutes(2);
    static final int MAX_ITEMS = 10;
    static final ZoneId BR = ZoneId.of("America/Sao_Paulo");
    private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"; // Crockford: sem I, L, O, U
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Documents documents;
    private final DocumentDeliveries deliveries;
    private final DocumentTemplates templates;
    private final EhrService ehr;
    private final ClinicalVaults vaults;
    private final Practitioners practitioners;
    private final VaultCrypto crypto;
    private final MedicationCatalog medications;
    private final ExamCatalog exams;
    private final DocumentPdf renderer;
    private final PadesSigner signer;
    private final PadesVerifier verifier;
    private final SignatureProviderPort provider;
    private final DeliveryNotifier notifier;
    private final CoreAudit audit;
    private final TransactionTemplate tx;
    private final String publicBaseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public DocumentService(Documents documents, DocumentDeliveries deliveries, DocumentTemplates templates, EhrService ehr, ClinicalVaults vaults,
            Practitioners practitioners, VaultCrypto crypto, MedicationCatalog medications, ExamCatalog exams, DocumentPdf renderer, PadesSigner signer,
            PadesVerifier verifier, SignatureProviderPort provider, DeliveryNotifier notifier, CoreAudit audit, PlatformTransactionManager txm,
            @Value("${doctor-explorer.public-base-url:http://localhost:3000}") String publicBaseUrl) {
        this.documents = documents;
        this.deliveries = deliveries;
        this.templates = templates;
        this.ehr = ehr;
        this.vaults = vaults;
        this.practitioners = practitioners;
        this.crypto = crypto;
        this.medications = medications;
        this.exams = exams;
        this.renderer = renderer;
        this.signer = signer;
        this.verifier = verifier;
        this.provider = provider;
        this.notifier = notifier;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    // ---------- rascunho ----------

    public DocumentView create(Caller c, DocumentViews.NewDocument n) {
        ClinicalDocument.Kind kind = parseKind(n.kind());
        if (n.patientId() == null) throw new ValidationProblemException(List.of(new FieldProblem("patientId", "Escolha o paciente do cofre.")));
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ehr.patientIn(o, n.patientId());
            Encounter e = ehr.encounterFor(o, n.patientId(), n.encounterId(), Encounter.Context.CONSULTA);
            ClinicalDocument d = new ClinicalDocument(UuidV7.generate(), o.vault().getId(), n.patientId(), e.getId(), c.userId(), kind);
            Content empty = new Content(List.of(), List.of(), "", "");
            d.saveDraft(crypto.encrypt(o.dek(), "document.content", d.getId(), json.writeValueAsString(empty)), hash(empty));
            documents.save(d);
            audit.record(c, "clinical.document_create", "document", d.getId(), d.getPatientId(), Map.of("kind", kind));
            return view(o, d);
        });
    }

    public DocumentView saveDraft(Caller c, UUID id, Content content) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = locked(o, id);
            requireAuthor(c, d);
            if (!d.editable()) throw notEditable(d);
            Content normalized = validate(content, d.getKind());
            d.saveDraft(crypto.encrypt(o.dek(), "document.content", d.getId(), json.writeValueAsString(normalized)), hash(normalized));
            documents.save(d);
            audit.record(c, "clinical.document_draft", "document", d.getId(), d.getPatientId(), Map.of());
            return view(o, d);
        });
    }

    public DocumentView read(Caller c, UUID id) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = owned(o, id);
            DocumentView v = view(o, d);
            audit.record(c, "clinical.document_read", "document", id, d.getPatientId(), Map.of("state", d.getState()));
            return v;
        });
    }

    public List<DocumentSummary> byPatient(Caller c, UUID patientId) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ehr.patientIn(o, patientId);
            List<DocumentSummary> out = documents.findByPatientIdOrderByCreatedAtDesc(patientId).stream().map(d -> {
                Content content = content(o, d);
                return new DocumentSummary(d.getId(), d.getKind().name(), d.getState().name(), content.medications().size() + content.exams().size(),
                        d.getSignedAt(), d.getValidUntil(), d.getCreatedAt());
            }).toList();
            audit.record(c, "clinical.document_list", "patient", patientId, patientId, Map.of("documents", out.size()));
            return out;
        });
    }

    /** Os 50 documentos mais recentes do médico no cofre, de todos os pacientes. */
    public List<DocumentViews.MyDocument> mine(Caller c) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            Map<UUID, String> names = new java.util.HashMap<>();
            List<DocumentViews.MyDocument> out = documents.findTop50ByVaultIdAndAuthorIdOrderByCreatedAtDesc(o.vault().getId(), c.userId()).stream().map(d -> {
                Content content = content(o, d);
                String name = names.computeIfAbsent(d.getPatientId(), pid -> ehr.patientIn(o, pid).name());
                return new DocumentViews.MyDocument(d.getId(), d.getKind().name(), d.getState().name(), d.getPatientId(), name,
                        content.medications().size() + content.exams().size(), d.getSignedAt(), d.getValidUntil(), d.getCreatedAt());
            }).toList();
            audit.record(c, "clinical.document_list", "document", null, null, Map.of("documents", out.size()));
            return out;
        });
    }

    // ---------- assinatura ----------

    /**
     * Confirmação com resumo (critério 3, heurística 5): o web mostra o resumo e manda o hash do conteúdo que o médico
     * viu; se o rascunho mudou desde então, 409. Com sudo, o estado vira ASSINANDO (já gravado) antes de falar com o
     * provedor; o resultado vira ASSINADA ou FALHA_ASSINATURA com causa e ação.
     */
    public DocumentView sign(Caller c, UUID id, String reviewedHash) {
        if (!c.sudoActive()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sudo-necessario", "Confirme sua identidade",
                    "Para assinar, confirme sua senha (e o código do autenticador, se o 2FA estiver ativo) em POST /api/v1/me/sudo.");
        }
        record Prepared(ClinicalDocument doc, Content content, PatientView patient, Practitioner me) {}
        Prepared p = tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = locked(o, id);
            requireAuthor(c, d);
            boolean stuck = d.getState() == ClinicalDocument.State.ASSINANDO && d.getUpdatedAt().isBefore(Instant.now().minus(STUCK_SIGNING));
            if (!d.editable() && !stuck) throw notEditable(d);
            if (reviewedHash == null || !reviewedHash.equals(d.getContentHash())) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "revisao-desatualizada", "Revise de novo",
                        "O documento mudou depois da revisão. Confira o resumo atual antes de assinar.");
            }
            Content content = content(o, d);
            List<FieldProblem> problems = new ArrayList<>();
            if (d.getKind() == ClinicalDocument.Kind.RECEITA && content.medications().isEmpty()) problems.add(new FieldProblem("medications", "Inclua ao menos um medicamento."));
            if (d.getKind() == ClinicalDocument.Kind.EXAME && content.exams().isEmpty()) problems.add(new FieldProblem("exams", "Inclua ao menos um exame."));
            if (d.getKind() == ClinicalDocument.Kind.EXAME && content.indication().isBlank()) problems.add(new FieldProblem("indication", "Informe a indicação clínica."));
            if (c.crm() == null || c.crm().isBlank()) problems.add(new FieldProblem("crm", "Seu CRM precisa estar verificado para assinar."));
            if (!problems.isEmpty()) throw new ValidationProblemException(problems);
            d.signing();
            documents.save(d);
            audit.record(c, "clinical.document_sign_start", "document", id, d.getPatientId(), Map.of("provider", provider.name()));
            return new Prepared(d, content, ehr.patientIn(o, d.getPatientId()), o.me());
        });

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String code = newCode();
        String url = publicBaseUrl + "/validar/" + code;
        boolean antimicrobial = p.content().medications().stream().anyMatch(m -> medications.byGgrem(m.ggrem()).map(MedicationCatalog.Medication::antimicrobial).orElse(false));
        LocalDate validUntil = p.doc().getKind() == ClinicalDocument.Kind.RECEITA && antimicrobial ? LocalDate.ofInstant(now, BR).plusDays(10) : null;
        PadesSigner.Signed signed;
        try {
            byte[] pdf = renderer.render(pdfModel(p.doc(), p.content(), p.patient(), p.me(), now, code, url, validUntil, antimicrobial));
            signed = signer.sign(pdf, new SignerIdentity(c.userId(), p.me().getName(), c.crm(), c.uf()),
                    p.doc().getKind() == ClinicalDocument.Kind.RECEITA ? "Prescrição eletrônica" : "Solicitação de exames", now);
        } catch (SignatureFailure f) {
            return tx.execute(s -> {
                Open o = ehr.open(c);
                ClinicalDocument d = locked(o, id);
                d.signatureFailed(f.getMessage());
                documents.save(d);
                audit.record(c, "clinical.document_sign_failed", "document", id, d.getPatientId(), Map.of("cause", f.failureCause()));
                return view(o, d);
            });
        }
        PadesVerifier.Check check = verifier.verify(signed.pdf());
        if (!check.valid()) throw new IllegalStateException("o provedor devolveu uma assinatura que não confere: " + check.problems());
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = locked(o, id);
            d.signed(sha256(code), crypto.encrypt(o.dek(), "document.code", id, code), crypto.encrypt(o.dek(), "document.pdf", id,
                    Base64.getEncoder().encodeToString(signed.pdf())), NoteSigner.sha256Hex(signed.pdf()), check.level(), provider.name(), signed.certSubject(),
                    signed.certSerial(), now, validUntil);
            documents.save(d);
            ehr.touchPatient(d.getPatientId());
            audit.record(c, "clinical.document_sign", "document", id, d.getPatientId(), Map.of("level", check.level(), "pdfSha256", d.getPdfSha256()));
            return view(o, d);
        });
    }

    /** PDF assinado para o médico baixar (só dele, auditado). */
    public byte[] pdf(Caller c, UUID id) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = owned(o, id);
            if (!d.signedOrAfter()) throw new ApiProblemException(HttpStatus.CONFLICT, "nao-assinado", "Ainda não assinado", "Assine o documento para baixar o PDF.");
            audit.record(c, "clinical.document_pdf", "document", id, d.getPatientId(), Map.of());
            return pdfBytes(o, d);
        });
    }

    // ---------- entrega e cancelamento ----------

    public Delivered deliver(Caller c, UUID id, String channel, String destination) {
        String ch = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("EMAIL", "SMS", "LINK").contains(ch)) throw new ValidationProblemException(List.of(new FieldProblem("channel", "Use EMAIL, SMS ou LINK.")));
        String dest = destination == null ? "" : destination.trim();
        if (ch.equals("EMAIL") && !dest.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ValidationProblemException(List.of(new FieldProblem("destination", "E-mail inválido.")));
        if (ch.equals("SMS") && !dest.replaceAll("\\D", "").matches("\\d{10,13}")) throw new ValidationProblemException(List.of(new FieldProblem("destination", "Celular com DDD (10 a 13 dígitos).")));
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String link = publicBaseUrl + "/receita/" + token;
        Instant expires = Instant.now().plus(DELIVERY_TTL);
        String masked = mask(ch, dest);
        Delivered out = tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = locked(o, id);
            requireAuthor(c, d);
            if (d.getState() != ClinicalDocument.State.ASSINADA && d.getState() != ClinicalDocument.State.ENVIADA) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "documento-estado", "Não dá para enviar agora",
                        d.getState() == ClinicalDocument.State.CANCELADA ? "Documento cancelado não é enviado." : "Assine o documento antes de enviar.");
            }
            deliveries.save(new DocumentDelivery(UuidV7.generate(), id, ch, masked, sha256(token), expires));
            d.delivered();
            documents.save(d);
            audit.record(c, "clinical.document_deliver", "document", id, d.getPatientId(), Map.of("channel", ch));
            return new Delivered(link, ch, masked, expires);
        });
        // a mensagem não leva conteúdo clínico (LGPD): só quem enviou e o link
        if (!ch.equals("LINK")) notifier.send(ch, dest, c.name(), link);
        return out;
    }

    public DocumentView cancel(Caller c, UUID id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 10) throw new ValidationProblemException(List.of(new FieldProblem("reason", "Explique o motivo do cancelamento (pelo menos 10 caracteres).")));
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = locked(o, id);
            requireAuthor(c, d);
            if (d.getState() != ClinicalDocument.State.ASSINADA && d.getState() != ClinicalDocument.State.ENVIADA) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "documento-estado", "Não dá para cancelar",
                        d.getState() == ClinicalDocument.State.CANCELADA ? "O documento já está cancelado." : "Rascunho não precisa de cancelamento: só não assine.");
            }
            d.cancel(why);
            documents.save(d);
            audit.record(c, "clinical.document_cancel", "document", id, d.getPatientId(), Map.of());
            return view(o, d);
        });
    }

    // ---------- público (farmácia e paciente, sem login) ----------

    /** Validador público: o mínimo para a farmácia, com a assinatura conferida agora. Mesmo 404 para formato e inexistente. */
    public PublicView validate(Caller publicCaller, String rawCode) {
        String code = normalizeCode(rawCode);
        return tx.execute(s -> {
            ClinicalDocument d = code == null ? null : documents.findByValidationCodeHash(sha256(code)).orElse(null);
            if (d == null) throw new ApiProblemException(HttpStatus.NOT_FOUND, "codigo-invalido", "Código não encontrado", "Confira o código impresso no documento.");
            Open o = openVault(d);
            Content content = content(o, d);
            PatientView patient = ehr.patientIn(o, d.getPatientId());
            Practitioner author = practitioners.findById(d.getAuthorId()).orElseThrow();
            PadesVerifier.Check check = verifier.verify(pdfBytes(o, d));
            LocalDate today = LocalDate.now(BR);
            String status;
            String text;
            if (d.getState() == ClinicalDocument.State.CANCELADA) {
                status = "CANCELADA";
                text = "Cancelada pelo médico. Não dispense.";
            } else if (d.getValidUntil() != null && today.isAfter(d.getValidUntil())) {
                status = "EXPIRADA";
                text = "Prazo de validade encerrado em " + d.getValidUntil().format(DocumentPdf.DATE) + ". Não dispense.";
            } else if (!check.valid()) {
                status = "ASSINATURA_INVALIDA";
                text = "A assinatura não confere. Não dispense.";
            } else {
                status = "VALIDA";
                text = "Assinatura conferida agora e dentro do prazo.";
            }
            List<String> items = new ArrayList<>();
            content.medications().forEach(m -> items.add(medications.byGgrem(m.ggrem()).map(MedicationCatalog.Medication::label).orElse(m.ggrem()) + " — " + Posology.text(m)));
            content.exams().forEach(e -> items.add(exams.byLoinc(e.loinc()).map(x -> x.name() + " (TUSS " + x.tuss() + ")").orElse(e.loinc())));
            audit.record(publicCaller, "clinical.document_validate", "document", d.getId(), d.getPatientId(), Map.of("status", status));
            return new PublicView(d.getKind().name(), status, text, d.getSignedAt(), d.getValidUntil(), author.getName(), author.getCrmNumber(), author.getCrmUf(),
                    initials(patient.name()), items, check.valid(), check.level(), "SIMULADO".equals(d.getSignatureProvider()), d.getPdfSha256(), d.getCanceledAt());
        });
    }

    /** Link do paciente: uso único e 7 dias; documento cancelado não é entregue. */
    public byte[] openDelivery(Caller publicCaller, String token) {
        return tx.execute(s -> {
            DocumentDelivery dl = token == null || token.length() > 100 ? null : deliveries.lockByTokenHash(sha256(token)).orElse(null);
            Instant now = Instant.now();
            if (dl == null || !dl.usable(now)) {
                throw new ApiProblemException(HttpStatus.GONE, "link-indisponivel", "Link indisponível",
                        "Este link já foi usado ou passou de 7 dias. Peça ao médico um novo envio.");
            }
            ClinicalDocument d = documents.findById(dl.getDocumentId()).orElseThrow();
            if (d.getState() == ClinicalDocument.State.CANCELADA) {
                throw new ApiProblemException(HttpStatus.GONE, "documento-cancelado", "Documento cancelado", "O médico cancelou este documento. Fale com ele.");
            }
            dl.used(now);
            deliveries.save(dl);
            audit.record(publicCaller, "clinical.document_delivery_open", "document", d.getId(), d.getPatientId(), Map.of("channel", dl.getChannel()));
            return pdfBytes(openVault(d), d);
        });
    }

    // ---------- modelos ----------

    public List<Map<String, Object>> templates(Caller c, String kind) {
        ClinicalDocument.Kind k = parseKind(kind);
        return templates.findByAuthorIdAndKindOrderByNameAsc(c.userId(), k).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("kind", t.getKind().name());
            m.put("name", t.getName());
            m.put("content", json.readValue(t.getItems(), Content.class));
            return m;
        }).toList();
    }

    public Map<String, Object> saveTemplate(Caller c, DocumentViews.TemplateInput in) {
        ClinicalDocument.Kind k = parseKind(in.kind());
        String name = in.name() == null ? "" : in.name().trim();
        if (name.length() < 3 || name.length() > 80) throw new ValidationProblemException(List.of(new FieldProblem("name", "Dê um nome de 3 a 80 caracteres ao modelo.")));
        Content content = validate(in.content(), k);
        Content withoutNotes = new Content(content.medications(), content.exams(), content.indication(), "");
        return tx.execute(s -> {
            ehr.open(c);
            if (templates.findByAuthorIdAndKindOrderByNameAsc(c.userId(), k).stream().anyMatch(t -> t.getName().equalsIgnoreCase(name))) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "modelo-existente", "Já existe um modelo com esse nome", "Escolha outro nome.");
            }
            DocumentTemplate t = templates.save(new DocumentTemplate(UuidV7.generate(), c.userId(), k, name, json.writeValueAsString(withoutNotes)));
            return Map.of("id", t.getId(), "name", t.getName(), "kind", k.name());
        });
    }

    public void deleteTemplate(Caller c, UUID id) {
        DocumentTemplate t = templates.findById(id).filter(x -> x.getAuthorId().equals(c.userId())).orElseThrow(() -> ApiProblemException.notFound("O modelo"));
        templates.delete(t);
    }

    // ---------- leitura para a fachada FHIR ----------

    public record Loaded(ClinicalDocument document, Content content, PatientView patient) {}

    public List<Loaded> loadByPatient(Caller c, UUID patientId) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ehr.patientIn(o, patientId);
            return documents.findByPatientIdOrderByCreatedAtDesc(patientId).stream().map(d -> new Loaded(d, content(o, d), ehr.patientIn(o, patientId))).toList();
        });
    }

    public Loaded load(Caller c, UUID id) {
        return tx.execute(s -> {
            Open o = ehr.open(c);
            ClinicalDocument d = owned(o, id);
            return new Loaded(d, content(o, d), ehr.patientIn(o, d.getPatientId()));
        });
    }

    // ---------- apoio ----------

    /** Itens válidos do catálogo; controle especial bloqueia com explicação (critério 1 ⚖️). */
    Content validate(Content in, ClinicalDocument.Kind kind) {
        if (in == null) throw new ValidationProblemException(List.of(new FieldProblem("content", "Envie os itens do documento.")));
        List<FieldProblem> problems = new ArrayList<>();
        List<MedicationInput> meds = in.medications() == null ? List.of() : in.medications();
        List<ExamInput> ex = in.exams() == null ? List.of() : in.exams();
        if (kind == ClinicalDocument.Kind.RECEITA && !ex.isEmpty()) problems.add(new FieldProblem("exams", "Receita não leva exames: use um pedido de exame."));
        if (kind == ClinicalDocument.Kind.EXAME && !meds.isEmpty()) problems.add(new FieldProblem("medications", "Pedido de exame não leva medicamentos."));
        if (meds.size() + ex.size() > MAX_ITEMS) problems.add(new FieldProblem("items", "No máximo " + MAX_ITEMS + " itens por documento."));
        List<MedicationInput> outMeds = new ArrayList<>();
        for (int i = 0; i < meds.size(); i++) {
            MedicationInput m = meds.get(i);
            String f = "medications[" + i + "]";
            var med = m == null ? java.util.Optional.<MedicationCatalog.Medication>empty() : medications.byGgrem(m.ggrem());
            if (med.isEmpty()) {
                problems.add(new FieldProblem(f + ".ggrem", "Escolha o medicamento na lista da CMED/Anvisa."));
                continue;
            }
            if (med.get().specialControl()) {
                problems.add(new FieldProblem(f + ".ggrem", med.get().substance() + " é de controle especial (Portaria 344/1998): fica fora do MVP. Use o receituário de controle especial."));
            }
            String dose = m.dose() == null ? "" : m.dose().trim();
            if (!dose.matches("\\d{1,4}([.,]\\d{1,3})?|\\d/\\d")) problems.add(new FieldProblem(f + ".dose", "Dose numérica (ex.: 1, 0,5, 1/2)."));
            if (m.doseUnit() == null || !Posology.UNITS.containsKey(m.doseUnit())) problems.add(new FieldProblem(f + ".doseUnit", "Escolha a unidade da dose."));
            if (m.route() == null || !Posology.ROUTES.containsKey(m.route())) problems.add(new FieldProblem(f + ".route", "Escolha a via de administração."));
            boolean interval = m.intervalHours() != null;
            boolean times = m.timesPerDay() != null;
            if (interval == times) problems.add(new FieldProblem(f + ".frequency", "Informe o intervalo em horas ou quantas vezes ao dia (um dos dois)."));
            if (interval && (m.intervalHours() < 1 || m.intervalHours() > 168)) problems.add(new FieldProblem(f + ".intervalHours", "Intervalo de 1 a 168 horas."));
            if (times && (m.timesPerDay() < 1 || m.timesPerDay() > 24)) problems.add(new FieldProblem(f + ".timesPerDay", "De 1 a 24 vezes ao dia."));
            boolean continuous = Boolean.TRUE.equals(m.continuous());
            if (!continuous && (m.durationDays() == null || m.durationDays() < 1 || m.durationDays() > 365)) {
                problems.add(new FieldProblem(f + ".durationDays", "Duração de 1 a 365 dias, ou marque uso contínuo."));
            }
            if (continuous && med.get().antimicrobial()) problems.add(new FieldProblem(f + ".continuous", "Antimicrobiano precisa de duração definida."));
            if (m.instructions() != null && m.instructions().length() > 300) problems.add(new FieldProblem(f + ".instructions", "Até 300 caracteres."));
            if (m.quantity() != null && m.quantity().length() > 60) problems.add(new FieldProblem(f + ".quantity", "Até 60 caracteres."));
            outMeds.add(new MedicationInput(med.get().ggrem(), dose, m.doseUnit(), m.route(), m.intervalHours(), m.timesPerDay(), continuous ? null : m.durationDays(),
                    continuous, blankToNull(m.quantity()), blankToNull(m.instructions())));
        }
        List<ExamInput> outEx = new ArrayList<>();
        for (int i = 0; i < ex.size(); i++) {
            ExamInput e = ex.get(i);
            var exam = e == null ? java.util.Optional.<ExamCatalog.Exam>empty() : exams.byLoinc(e.loinc());
            if (exam.isEmpty()) problems.add(new FieldProblem("exams[" + i + "].loinc", "Escolha o exame na lista (LOINC com TUSS)."));
            else if (outEx.stream().noneMatch(x -> x.loinc().equals(exam.get().loinc()))) outEx.add(new ExamInput(exam.get().loinc()));
        }
        String indication = in.indication() == null ? "" : in.indication().trim();
        String notes = in.notes() == null ? "" : in.notes().trim();
        if (indication.length() > 500) problems.add(new FieldProblem("indication", "Até 500 caracteres."));
        if (notes.length() > 500) problems.add(new FieldProblem("notes", "Até 500 caracteres."));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        return new Content(outMeds, outEx, indication, notes);
    }

    private DocumentPdf.Model pdfModel(ClinicalDocument d, Content content, PatientView patient, Practitioner me, Instant at, String code, String url,
            LocalDate validUntil, boolean antimicrobial) {
        List<DocumentPdf.Item> items = new ArrayList<>();
        for (MedicationInput m : content.medications()) {
            MedicationCatalog.Medication med = medications.byGgrem(m.ggrem()).orElseThrow();
            List<String> details = new ArrayList<>();
            details.add(Posology.text(m));
            if (m.quantity() != null) details.add("Quantidade: " + m.quantity());
            items.add(new DocumentPdf.Item(med.substance() + " · " + med.presentation(), details));
        }
        for (ExamInput e : content.exams()) {
            ExamCatalog.Exam ex = exams.byLoinc(e.loinc()).orElseThrow();
            items.add(new DocumentPdf.Item(ex.name(), List.of("TUSS " + ex.tuss() + " · LOINC " + ex.loinc())));
        }
        List<String> footer = new ArrayList<>();
        if (antimicrobial) footer.add("Antimicrobiano: receita em 2 vias, a farmácia retém a segunda (RDC 471/2021). Válida até " + validUntil.format(DocumentPdf.DATE) + ".");
        return new DocumentPdf.Model(d.getKind() == ClinicalDocument.Kind.RECEITA ? "RECEITUÁRIO" : "SOLICITAÇÃO DE EXAMES", me.getName(), me.getCrmNumber(),
                me.getCrmUf(), patient.name(), patient.birthDate(), at, items, d.getKind() == ClinicalDocument.Kind.EXAME ? content.indication() : null,
                content.notes(), code, url, footer, "SIMULADO".equals(provider.name()));
    }

    private DocumentView view(Open o, ClinicalDocument d) {
        Content content = content(o, d);
        PatientView patient = ehr.patientIn(o, d.getPatientId());
        List<MedicationView> meds = content.medications().stream().map(m -> {
            var med = medications.byGgrem(m.ggrem());
            return new MedicationView(m, med.map(MedicationCatalog.Medication::label).orElse(m.ggrem()), med.map(MedicationCatalog.Medication::substance).orElse(""),
                    med.map(MedicationCatalog.Medication::presentation).orElse(""), med.map(MedicationCatalog.Medication::antimicrobial).orElse(false), Posology.text(m));
        }).toList();
        List<ExamView> ex = content.exams().stream().map(e -> exams.byLoinc(e.loinc()).map(x -> new ExamView(x.loinc(), x.name(), x.tuss(), x.tussTerm()))
                .orElse(new ExamView(e.loinc(), e.loinc(), "", ""))).toList();
        String code = d.getValidationCodeEnc() == null ? null : crypto.decrypt(o.dek(), "document.code", d.getId(), d.getValidationCodeEnc());
        List<DeliveryView> dl = deliveries.findByDocumentIdOrderByCreatedAtDesc(d.getId()).stream()
                .map(x -> new DeliveryView(x.getChannel(), x.getDestinationMasked(), x.getExpiresAt(), x.getUsedAt(), x.getCreatedAt())).toList();
        return new DocumentView(d.getId(), d.getKind().name(), d.getState().name(), d.getPatientId(), patient.name(), d.getEncounterId(), meds, ex,
                content.indication(), content.notes(), d.getContentHash(), code, code == null ? null : publicBaseUrl + "/validar/" + code, d.getSignedAt(),
                d.getValidUntil(), d.getSignatureLevel(), d.getSignatureProvider(), d.getCertSubject(), d.getPdfSha256(), d.getFailureReason(), d.getCancelReason(),
                d.getCanceledAt(), dl, d.getCreatedAt(), d.getUpdatedAt());
    }

    private Content content(Open o, ClinicalDocument d) {
        return json.readValue(crypto.decrypt(o.dek(), "document.content", d.getId(), d.getContentEnc()), Content.class);
    }

    private byte[] pdfBytes(Open o, ClinicalDocument d) {
        return Base64.getDecoder().decode(crypto.decrypt(o.dek(), "document.pdf", d.getId(), d.getPdfEnc()));
    }

    /** Cofre do documento para as rotas públicas (sem médico na requisição). */
    private Open openVault(ClinicalDocument d) {
        var v = vaults.findById(d.getVaultId()).orElseThrow();
        return new Open(v, crypto.unwrapDek(v.getId(), v.getWrappedDek()), practitioners.findById(d.getAuthorId()).orElseThrow());
    }

    private ClinicalDocument owned(Open o, UUID id) {
        return documents.findById(id).filter(d -> d.getVaultId().equals(o.vault().getId())).orElseThrow(() -> ApiProblemException.notFound("O documento"));
    }

    private ClinicalDocument locked(Open o, UUID id) {
        return documents.lockById(id).filter(d -> d.getVaultId().equals(o.vault().getId())).orElseThrow(() -> ApiProblemException.notFound("O documento"));
    }

    private static void requireAuthor(Caller c, ClinicalDocument d) {
        if (!d.getAuthorId().equals(c.userId())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "nao-autor", "Só o autor altera o documento", "Peça ao autor ou emita um documento seu.");
        }
    }

    private static ApiProblemException notEditable(ClinicalDocument d) {
        return new ApiProblemException(HttpStatus.CONFLICT, "documento-imutavel", "Documento já assinado",
                d.getState() == ClinicalDocument.State.ASSINANDO ? "A assinatura está em andamento. Aguarde o resultado." : "Documento assinado não muda: cancele com motivo e emita outro.");
    }

    private static ClinicalDocument.Kind parseKind(String kind) {
        try {
            return ClinicalDocument.Kind.valueOf(kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationProblemException(List.of(new FieldProblem("kind", "Use RECEITA ou EXAME.")));
        }
    }

    String hash(Content c) {
        return NoteSigner.sha256Hex(json.writeValueAsString(c).getBytes(StandardCharsets.UTF_8));
    }

    static String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Aceita minúsculas, espaços e as letras que o Crockford troca (O→0, I/L→1); devolve "XXXX-XXXX-XXXX-XXXX". */
    static String normalizeCode(String raw) {
        if (raw == null) return null;
        String c = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "").replace('O', '0').replace('I', '1').replace('L', '1');
        if (!c.matches("[" + CODE_ALPHABET + "]{16}")) return null;
        return c.substring(0, 4) + "-" + c.substring(4, 8) + "-" + c.substring(8, 12) + "-" + c.substring(12);
    }

    static String sha256(String s) {
        return NoteSigner.sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.trim().split("\\s+")) {
            if (w.length() > 2 && Character.isLetter(w.charAt(0))) sb.append(Character.toUpperCase(w.charAt(0))).append(". ");
        }
        return sb.toString().trim();
    }

    static String mask(String channel, String dest) {
        if (channel.equals("EMAIL")) {
            int at = dest.indexOf('@');
            return dest.charAt(0) + "***" + dest.substring(at);
        }
        if (channel.equals("SMS")) {
            String digits = dest.replaceAll("\\D", "");
            return "(**) *****-" + digits.substring(digits.length() - 4);
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
