package com.doctorexplorer.core.audit;

import com.doctorexplorer.core.audit.AuditChain.Verification;
import com.doctorexplorer.core.common.Hashing;
import com.doctorexplorer.core.storage.ObjectStore;
import com.doctorexplorer.core.storage.StorageProperties;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/**
 * RF13 · consulta e exportação da auditoria. Cada consulta verifica a cadeia de hash dos escopos lidos, mascara o
 * id do paciente para quem não pode vê-lo e gera um evento {@code audit.view} (auditoria da auditoria).
 */
@Service
public class AuditService {

    public static final int MAX_DAYS = 31;
    public static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);
    public static final String MASK = "[oculto]";

    /**
     * Quem consulta e o que pode ver. {@code categories} nulo = todas; {@code scopes} são as cadeias lidas
     * ("platform" ou "inst:{id}" da árvore); {@code auditScope} recebe o evento audit.view.
     */
    public record Viewer(UUID userId, String label, Set<String> categories, boolean mayRevealPatient, List<String> scopes, UUID institutionId) {}

    public record Query(LocalDate from, LocalDate to, UUID actorId, String action, String resourceType, String justification,
            boolean revealPatient, int page, int size) {}

    public record ChainReport(boolean intact, List<Map<String, Object>> broken, int scopesChecked, int eventsChecked) {}

    public record Result(List<AuditEvent> events, int total, int page, int size, ChainReport chain, boolean patientRevealed) {}

    private final CassandraAuditStore store;
    private final AuditLog log;
    private final AuditExports exports;
    private final AuditSigner signer;
    private final ObjectStore objects;
    private final StorageProperties storage;

    public AuditService(CassandraAuditStore store, AuditLog log, AuditExports exports, AuditSigner signer, ObjectStore objects,
            StorageProperties storage) {
        this.store = store;
        this.log = log;
        this.exports = exports;
        this.signer = signer;
        this.objects = objects;
        this.storage = storage;
    }

    public Query normalize(Query q) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate to = q.to() == null ? today : q.to();
        LocalDate from = q.from() == null ? to.minusDays(6) : q.from();
        List<FieldProblem> problems = new ArrayList<>();
        if (from.isAfter(to)) problems.add(new FieldProblem("from", "A data inicial vem depois da final."));
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) problems.add(new FieldProblem("to", "O período tem no máximo " + MAX_DAYS + " dias por consulta."));
        String just = q.justification() == null ? "" : q.justification().trim();
        if (q.revealPatient() && just.length() < 10) {
            problems.add(new FieldProblem("justification", "Para ver o id do paciente, escreva a justificativa (pelo menos 10 caracteres). Ela fica registrada."));
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        int size = q.size() <= 0 ? 50 : Math.min(q.size(), 200);
        return new Query(from, to, q.actorId(), blankToNull(q.action()), blankToNull(q.resourceType()), just, q.revealPatient(), Math.max(q.page(), 0), size);
    }

    public Result search(Viewer v, Query raw) {
        Query q = normalize(raw);
        if (q.revealPatient() && !v.mayRevealPatient()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem permissão para ver o paciente",
                    "Só o auditor da instituição e o Encarregado veem o id do paciente, sempre com justificativa.");
        }
        Collected c = collect(v, q);
        int from = Math.min(q.page() * q.size(), c.events.size());
        int to = Math.min(from + q.size(), c.events.size());
        AuditRecord view = AuditRecord.of("audit.view").resource("audit_query", v.label()).justification(q.justification())
                .detail("from", q.from()).detail("to", q.to()).detail("actorId", q.actorId()).detail("action", q.action())
                .detail("resourceType", q.resourceType()).detail("revealPatient", q.revealPatient()).detail("results", c.events.size());
        if (v.institutionId() != null) view.institution(v.institutionId());
        log.record(view);
        return new Result(c.events.subList(from, to), c.events.size(), q.page(), q.size(), c.chain, q.revealPatient());
    }

    private record Collected(List<AuditEvent> events, ChainReport chain) {}

    private Collected collect(Viewer v, Query q) {
        List<AuditEvent> all = new ArrayList<>();
        List<Map<String, Object>> broken = new ArrayList<>();
        int checked = 0;
        for (String scope : v.scopes()) {
            // só a cadeia confirmada, ancorada na cabeça gravada: provisórios de corrida ficam de fora e sumiço do fim aparece
            AuditChain.Resolved resolved = store.committed(scope, q.from(), q.to());
            List<AuditEvent> events = resolved.events();
            checked += events.size();
            Verification ver = resolved.verification();
            if (!ver.intact()) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("scope", scope);
                b.put("seq", ver.firstBrokenSeq());
                b.put("reason", ver.reason());
                broken.add(b);
            }
            // a cadeia "platform" repete os eventos de instituição; ao ler várias cadeias, evita duplicar
            for (AuditEvent e : events) if (all.stream().noneMatch(x -> x.id().equals(e.id()))) all.add(e);
        }
        List<AuditEvent> filtered = all.stream()
                .filter(e -> v.categories() == null || v.categories().contains(e.category()))
                .filter(e -> q.actorId() == null || q.actorId().equals(e.actorId()))
                .filter(e -> q.action() == null || e.action().startsWith(q.action()))
                .filter(e -> q.resourceType() == null || q.resourceType().equals(e.resourceType()))
                .map(e -> e.patientRef() == null || q.revealPatient() ? e : e.withPatientRef(MASK))
                .sorted(Comparator.comparing(AuditEvent::at).reversed().thenComparing(AuditEvent::seq, Comparator.reverseOrder()))
                .toList();
        return new Collected(filtered, new ChainReport(broken.isEmpty(), broken, v.scopes().size(), checked));
    }

    /** Gera o CSV assinado (estados SOLICITADA → GERANDO → PRONTA) e registra audit.export. */
    @Transactional
    public AuditExport export(Viewer v, Query raw) {
        Query q = normalize(raw);
        if (q.justification().length() < 10) {
            throw new ValidationProblemException(List.of(new FieldProblem("justification", "Toda exportação precisa de justificativa (pelo menos 10 caracteres).")));
        }
        if (q.revealPatient() && !v.mayRevealPatient()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem permissão para ver o paciente",
                    "Só o auditor da instituição e o Encarregado exportam o id do paciente.");
        }
        String filters = "from=" + q.from() + ";to=" + q.to() + ";actorId=" + n(q.actorId()) + ";action=" + n(q.action()) + ";resourceType=" + n(q.resourceType());
        AuditExport ex = exports.save(new AuditExport(v.userId(), v.label(), String.join(",", v.scopes()), filters, q.justification(), q.revealPatient()));
        ex.generating();
        try {
            Collected c = collect(v, q);
            byte[] csv = toCsv(c.events, c.chain).getBytes(StandardCharsets.UTF_8);
            String key = "exports/" + ex.getId() + "/auditoria.csv";
            String signature = signer.signBase64(csv);
            objects.put(storage.buckets().auditExports(), key, csv, "text/csv; charset=utf-8", Map.of("sha256", Hashing.sha256Hex(csv)));
            objects.put(storage.buckets().auditExports(), key + ".sig", signature.getBytes(StandardCharsets.US_ASCII), "text/plain", Map.of());
            ex.ready(key, Hashing.sha256Hex(csv), signature, c.events.size(), Instant.now());
        } catch (RuntimeException e) {
            ex.failed();
            exports.save(ex);
            throw e;
        }
        AuditRecord r = AuditRecord.of("audit.export").resource("audit_export", ex.getId()).justification(q.justification())
                .detail("filters", filters).detail("events", ex.getEventCount()).detail("revealPatient", q.revealPatient()).detail("sha256", ex.getSha256());
        if (v.institutionId() != null) r.institution(v.institutionId());
        log.record(r);
        return exports.save(ex);
    }

    public record Download(AuditExport export, URI csvUrl, URI signatureUrl) {}

    /**
     * URL pré-assinada de 15 min; só quem pediu e só com a mesma visão de agora (papel, vínculo e 2FA conferidos de
     * novo pelo controller que monta a visão); depois de 24 h a exportação expira.
     */
    @Transactional
    public Download download(Viewer v, UUID exportId) {
        AuditExport ex = exports.findById(exportId).filter(e -> e.getRequestedBy().equals(v.userId()) && v.label().equals(e.getViewerLabel()))
                .orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado",
                        "A exportação não existe, não é sua ou foi gerada com um papel que você não tem mais."));
        if (!Set.copyOf(v.scopes()).containsAll(List.of(ex.getScope().split(","))) || (ex.isRevealPatient() && !v.mayRevealPatient())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem permissão para esta exportação",
                    "Seu acesso atual não cobre mais as instituições ou o id do paciente desta exportação.");
        }
        Instant now = Instant.now();
        if (!ex.downloadable(now)) {
            exports.save(ex);
            throw new ApiProblemException(HttpStatus.GONE, "exportacao-indisponivel", "Exportação indisponível",
                    ex.getState() == AuditExport.State.EXPIRADA ? "A exportação ficou disponível por 24 h. Gere outra." : "A exportação está em " + ex.getState() + ".");
        }
        ex.downloaded(now);
        exports.save(ex);
        String bucket = storage.buckets().auditExports();
        log.record(AuditRecord.of("audit.export_download").resource("audit_export", ex.getId()));
        return new Download(ex, objects.presignGet(bucket, ex.getObjectKey(), DOWNLOAD_TTL, "auditoria-" + ex.getId() + ".csv"),
                objects.presignGet(bucket, ex.getObjectKey() + ".sig", DOWNLOAD_TTL, "auditoria-" + ex.getId() + ".csv.sig"));
    }

    public List<AuditExport> myExports(Viewer v) {
        return exports.findTop20ByRequestedByAndViewerLabelOrderByCreatedAtDesc(v.userId(), v.label());
    }

    static String toCsv(List<AuditEvent> events, ChainReport chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Doctor Explorer · exportação de auditoria · cadeia ").append(chain.intact() ? "íntegra" : "COM FALHA").append('\n');
        sb.append("scope,seq,at,actor_id,actor_roles,action,resource_type,resource_id,patient_ref,outcome,justification,prev_hash,hash\n");
        for (AuditEvent e : events) {
            sb.append(String.join(",", csv(e.scope()), String.valueOf(e.seq()), csv(e.at().toString()), csv(n(e.actorId())), csv(e.actorRoles()),
                    csv(e.action()), csv(e.resourceType()), csv(e.resourceId()), csv(e.patientRef()), csv(e.outcome()), csv(e.justification()),
                    csv(e.prevHash()), csv(e.hash()))).append('\n');
        }
        return sb.toString();
    }

    /** RFC 4180 + defesa contra injeção de fórmula em planilhas (=, +, -, @ viram texto). */
    static String csv(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("'")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static String n(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
