package com.doctorexplorer.core.media;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.media.pipeline.DeidentificationPipeline;
import com.doctorexplorer.core.media.pipeline.DeidentificationReport;
import com.doctorexplorer.core.media.pipeline.DicomDeidentifier;
import com.doctorexplorer.core.media.pipeline.FaceBlurrer;
import com.doctorexplorer.core.media.pipeline.ImageDecoder;
import com.doctorexplorer.core.media.pipeline.MediaRejected;
import com.doctorexplorer.core.media.pipeline.OcrRedactor;
import com.doctorexplorer.core.media.pipeline.Region;
import com.doctorexplorer.core.storage.ObjectStore;
import com.doctorexplorer.core.storage.StorageProperties;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * RF05 · mídia de casos. O arquivo nunca passa pela API: vai direto ao S3 de quarentena por URL pré-assinada com
 * tipo e tamanho fixos. A anonimização roda em segundo plano depois do commit; a prévia fica privada até o autor
 * aprovar; só a versão anonimizada vai para o bucket publicado e o original é apagado.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/heic", "image/heif");
    static final Set<String> DICOM_TYPES = Set.of("application/dicom");
    static final Duration PREVIEW_TTL = Duration.ofMinutes(5);
    static final Duration VIEW_TTL = Duration.ofMinutes(10);
    static final Duration ORIGINAL_MAX_AGE = Duration.ofHours(24);
    static final int MAX_MANUAL_REGIONS = 50;
    static final int MAX_ATTEMPTS = 3;
    static final Duration QUEUED_STALE = Duration.ofMinutes(2);
    static final Duration PROCESSING_STALE = Duration.ofMinutes(10);

    private final MediaAssets assets;
    private final ObjectStore objects;
    private final StorageProperties storage;
    private final MediaProperties props;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final DeidentificationPipeline pipeline;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public MediaService(MediaAssets assets, ObjectStore objects, StorageProperties storage, MediaProperties props, AuditLog audit,
            PlatformTransactionManager txm) {
        this.assets = assets;
        this.objects = objects;
        this.storage = storage;
        this.props = props;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        this.pipeline = new DeidentificationPipeline(new ImageDecoder(props.maxPixels(), props.heifConvert(), props.heifInfo()),
                new DicomDeidentifier(props.maxPixels()), new OcrRedactor(props.tesseract(), props.ocrLanguages(), props.ocrMinConfidence()),
                new FaceBlurrer());
        try {
            objects.allowBrowserUploads(storage.buckets().mediaQuarantine(), props.corsOrigins());
        } catch (RuntimeException e) {
            // sem CORS o navegador não envia; a API sobe mesmo assim e o envio falha visível no web
            log.warn("Não configurou CORS do bucket de quarentena: {}", e.toString());
        }
    }

    public record Upload(MediaAsset asset, URI uploadUrl) {}

    public Upload requestUpload(UUID userId, String contentType, long size, boolean dermatology, Boolean blurFaces) {
        String type = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        List<FieldProblem> problems = new ArrayList<>();
        MediaAsset.Kind kind = IMAGE_TYPES.contains(type) ? MediaAsset.Kind.IMAGE : DICOM_TYPES.contains(type) ? MediaAsset.Kind.DICOM : null;
        if (kind == null) {
            problems.add(new FieldProblem("contentType", type.startsWith("video/") ? "Vídeo fica fora do MVP. Envie uma imagem (JPEG, PNG, HEIC) ou DICOM."
                    : "Tipo não aceito. Envie JPEG, PNG, HEIC ou DICOM."));
        }
        long max = kind == MediaAsset.Kind.DICOM ? props.maxDicomBytes() : props.maxImageBytes();
        if (size <= 0 || size > max) problems.add(new FieldProblem("size", "O arquivo precisa ter até " + max / (1024 * 1024) + " MB."));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        MediaAsset a = tx.execute(s -> assets.save(new MediaAsset(userId, kind, type, size, dermatology, blurFaces)));
        URI url = objects.presignPut(storage.buckets().mediaQuarantine(), a.getQuarantineKey(), type, size, MediaAsset.UPLOAD_TTL);
        audit.record(AuditRecord.of("media.upload_request").actor(userId, "").resource("media", a.getId()).detail("type", type).detail("size", size)
                .detail("dermatology", dermatology));
        return new Upload(a, url);
    }

    /** O navegador avisa que terminou o PUT: confere tamanho no S3 e agenda a anonimização (depois do commit). */
    public MediaAsset complete(UUID userId, UUID id) {
        MediaAsset a = tx.execute(s -> {
            MediaAsset m = owned(userId, id);
            if (m.getState() != MediaAsset.State.UPLOAD_PENDENTE) return m;
            if (Instant.now().isAfter(m.getUploadExpiresAt())) {
                m.expired();
                return assets.save(m);
            }
            Long actual = objects.size(storage.buckets().mediaQuarantine(), m.getQuarantineKey()).orElseThrow(() ->
                    new ApiProblemException(HttpStatus.CONFLICT, "upload-ausente", "Arquivo não recebido", "O envio não chegou ao armazenamento. Tente de novo."));
            if (actual != m.getDeclaredSize()) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "upload-diferente", "Arquivo diferente do declarado", "O tamanho recebido não confere com o declarado.");
            }
            m.received();
            MediaAsset saved = assets.save(m);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    workers.submit(() -> process(saved.getId()));
                }
            });
            return saved;
        });
        return a;
    }

    /** Worker: lê a quarentena em memória, anonimiza e grava a prévia privada. Falha fechada. */
    void process(UUID id) {
        MediaAsset a = tx.execute(s -> {
            // a trava evita dois workers (reenvio, retomada em outra instância) processando a mesma mídia
            MediaAsset m = assets.lockById(id).orElseThrow();
            if (m.getState() != MediaAsset.State.QUARENTENA) return null;
            m.processing();
            return assets.save(m);
        });
        if (a == null) return;
        String quarantine = storage.buckets().mediaQuarantine();
        try {
            byte[] original = objects.get(quarantine, a.getQuarantineKey()).orElseThrow(() -> new MediaRejected("Arquivo não encontrado na quarentena.")).bytes();
            DeidentificationPipeline.Output out = pipeline.run(original, a.getDeclaredType(), a.shouldBlurFaces());
            String previewKey = "previews/" + a.getId() + ("image/jpeg".equals(out.contentType()) ? ".jpg" : ".png");
            objects.put(quarantine, previewKey, out.bytes(), out.contentType(), Map.of());
            String report = json.writeValueAsString(out.report());
            tx.executeWithoutResult(s -> {
                MediaAsset m = assets.findById(id).orElseThrow();
                m.readyForReview(previewKey, out.contentType(), out.width(), out.height(), report, Instant.now());
                assets.save(m);
            });
            audit.record(AuditRecord.of("media.processed").actor(a.getOwnerId(), "").resource("media", id).detail("textRegions", out.report().textRegionsRedacted())
                    .detail("faces", out.report().facesBlurred()).detail("metadataGroups", out.report().removedMetadata().size()));
        } catch (MediaRejected e) {
            endWithError(id, a, true, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Anonimização falhou para a mídia {}: {}", id, e.toString());
            endWithError(id, a, false, "Não foi possível anonimizar com segurança. Nada foi publicado; tente de novo mais tarde.");
        }
    }

    private void endWithError(UUID id, MediaAsset a, boolean rejected, String reason) {
        boolean deleted = deleteQuietly(storage.buckets().mediaQuarantine(), a.getQuarantineKey());
        tx.executeWithoutResult(s -> {
            MediaAsset m = assets.lockById(id).orElseThrow();
            if (rejected) m.rejected(reason);
            else m.failed(reason);
            // só marca apagado se o S3 confirmou; senão o job de 24 h tenta de novo
            if (deleted) m.originalDeleted(Instant.now());
            assets.save(m);
        });
        audit.record(AuditRecord.of("media.processed").actor(a.getOwnerId(), "").resource("media", id).outcome(rejected ? "rejected" : "failed"));
    }

    public Map<String, Object> view(UUID userId, UUID id) {
        MediaAsset a = owned(userId, id);
        Map<String, Object> v = view(a);
        if (a.getState() == MediaAsset.State.AGUARDANDO_REVISAO) {
            v.put("previewUrl", objects.presignGet(storage.buckets().mediaQuarantine(), a.getPreviewKey(), PREVIEW_TTL, null).toString());
        }
        if (a.getState() == MediaAsset.State.APROVADO) {
            v.put("url", objects.presignGet(storage.buckets().mediaPublished(), a.getPublishedKey(), VIEW_TTL, null).toString());
        }
        return v;
    }

    /** Região normalizada (0–1) sobre a prévia; vira pixels pelo tamanho da saída. */
    public record NormRegion(double x, double y, double w, double h) {}

    public Map<String, Object> redact(UUID userId, UUID id, int previewVersion, List<NormRegion> regions) {
        if (regions == null || regions.isEmpty() || regions.size() > MAX_MANUAL_REGIONS) {
            throw new ValidationProblemException(List.of(new FieldProblem("regions", "Envie de 1 a " + MAX_MANUAL_REGIONS + " áreas para cobrir.")));
        }
        for (NormRegion r : regions) {
            if (r.w() <= 0 || r.h() <= 0 || r.x() < 0 || r.y() < 0 || r.x() > 1 || r.y() > 1) {
                throw new ValidationProblemException(List.of(new FieldProblem("regions", "Cada área precisa estar dentro da imagem (coordenadas de 0 a 1).")));
            }
        }
        owned(userId, id);
        // ler, tarjar e regravar a prévia com a linha travada: duas abas não sobrescrevem as tarjas uma da outra
        tx.executeWithoutResult(s -> {
            MediaAsset m = assets.lockById(id).orElseThrow();
            requireReview(m, previewVersion);
            List<Region> px = new ArrayList<>();
            for (NormRegion r : regions) {
                px.add(new Region((int) Math.floor(r.x() * m.getWidth()), (int) Math.floor(r.y() * m.getHeight()),
                        (int) Math.ceil(r.w() * m.getWidth()), (int) Math.ceil(r.h() * m.getHeight())));
            }
            String quarantine = storage.buckets().mediaQuarantine();
            byte[] current = objects.get(quarantine, m.getPreviewKey()).orElseThrow().bytes();
            objects.put(quarantine, m.getPreviewKey(), DeidentificationPipeline.applyManual(current, m.getOutputType(), px), m.getOutputType(), Map.of());
            m.redacted(json.writeValueAsString(readReport(m).withManual(px.size())));
            assets.save(m);
            audit.record(AuditRecord.of("media.redaction").actor(userId, "").resource("media", id).detail("regions", px.size()));
        });
        return view(userId, id);
    }

    /** Aprovação exige a versão atual da prévia (o autor viu o que vai publicar) e a confirmação explícita. */
    public Map<String, Object> approve(UUID userId, UUID id, int previewVersion, boolean reviewed) {
        if (!reviewed) {
            throw new ValidationProblemException(List.of(new FieldProblem("reviewed", "Confirme que revisou a prévia e a lista do que foi removido.")));
        }
        owned(userId, id);
        MediaAsset approved = tx.execute(s -> {
            MediaAsset m = assets.lockById(id).orElseThrow();
            requireReview(m, previewVersion);
            String ext = "image/jpeg".equals(m.getOutputType()) ? ".jpg" : ".png";
            String publishedKey = "published/" + m.getId() + ext;
            objects.copy(storage.buckets().mediaQuarantine(), m.getPreviewKey(), storage.buckets().mediaPublished(), publishedKey);
            m.approved(publishedKey, Instant.now());
            audit.record(AuditRecord.of("media.approve").actor(userId, "").resource("media", id).detail("previewVersion", previewVersion));
            return assets.save(m);
        });
        deleteFromQuarantine(approved);
        return view(userId, id);
    }

    public void discard(UUID userId, UUID id) {
        owned(userId, id);
        MediaAsset discarded = tx.execute(s -> {
            MediaAsset m = assets.lockById(id).orElseThrow();
            if (m.getState() == MediaAsset.State.APROVADO && m.getPostId() != null) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "midia-publicada", "Mídia já publicada",
                        "Ela faz parte de uma publicação; exclua a publicação para removê-la.");
            }
            if (m.getState().isFinal() && m.getState() != MediaAsset.State.APROVADO) return null;
            if (m.getPublishedKey() != null && !deleteQuietly(storage.buckets().mediaPublished(), m.getPublishedKey())) {
                throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "armazenamento-indisponivel", "Tente de novo",
                        "Não foi possível remover a imagem agora. Nada mudou; tente de novo em instantes.");
            }
            // aprovada e não usada: some do bucket publicado e fica como descartada
            m.discarded();
            audit.record(AuditRecord.of("media.discard").actor(userId, "").resource("media", id));
            return assets.save(m);
        });
        if (discarded != null) deleteFromQuarantine(discarded);
    }

    /** Apaga original e prévia da quarentena; marca o original como apagado só se o S3 confirmou (critério 6). */
    private void deleteFromQuarantine(MediaAsset m) {
        String quarantine = storage.buckets().mediaQuarantine();
        boolean original = deleteQuietly(quarantine, m.getQuarantineKey());
        if (m.getPreviewKey() != null) deleteQuietly(quarantine, m.getPreviewKey());
        if (original) {
            tx.executeWithoutResult(s -> assets.findById(m.getId()).ifPresent(x -> {
                x.originalDeleted(Instant.now());
                assets.save(x);
            }));
        }
    }

    /** Antes de publicar: toda mídia citada é do autor, está aprovada e ainda não pertence a outra publicação. */
    public List<MediaAsset> requireAttachable(UUID userId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 10) throw new ValidationProblemException(List.of(new FieldProblem("mediaIds", "No máximo 10 imagens por publicação.")));
        List<MediaAsset> found = assets.findByIdInAndOwnerId(ids, userId);
        if (found.size() != Set.copyOf(ids).size() || found.stream().anyMatch(m -> m.getState() != MediaAsset.State.APROVADO || m.getPostId() != null)) {
            throw new ValidationProblemException(List.of(new FieldProblem("mediaIds", "Só entram imagens suas, revisadas e aprovadas, ainda não usadas em outra publicação.")));
        }
        return found;
    }

    public void attach(UUID postId, List<MediaAsset> media) {
        tx.executeWithoutResult(s -> media.forEach(m -> {
            MediaAsset fresh = assets.lockById(m.getId()).orElseThrow();
            if (fresh.getPostId() != null) throw new IllegalStateException("mídia já anexada");
            fresh.attachTo(postId);
            assets.save(fresh);
        }));
    }

    /** Para quem já pode ver a publicação (checado pela comunidade): URLs de leitura de 10 min. */
    public List<Map<String, Object>> forPost(UUID postId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MediaAsset m : assets.findByPostIdAndState(postId, MediaAsset.State.APROVADO)) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", m.getId());
            v.put("url", objects.presignGet(storage.buckets().mediaPublished(), m.getPublishedKey(), VIEW_TTL, null).toString());
            v.put("width", m.getWidth());
            v.put("height", m.getHeight());
            v.put("contentType", m.getOutputType());
            out.add(v);
        }
        return out;
    }

    public int countForPost(UUID postId) {
        return (int) assets.countByPostIdAndState(postId, MediaAsset.State.APROVADO);
    }

    /**
     * Job (também roda logo após subir): envio não feito em 15 min expira; original com mais de 24 h é apagado em
     * qualquer estado e só sai da lista quando o S3 confirma (critério 6); processamento perdido num reinício é
     * retomado, até {@value #MAX_ATTEMPTS} tentativas.
     */
    @Scheduled(fixedDelayString = "${doctor-explorer.jobs.media-cleanup-ms:600000}")
    public void cleanup() {
        Instant now = Instant.now();
        for (MediaAsset m : assets.findByStateAndUploadExpiresAtBefore(MediaAsset.State.UPLOAD_PENDENTE, now)) {
            boolean deleted = deleteQuietly(storage.buckets().mediaQuarantine(), m.getQuarantineKey());
            tx.executeWithoutResult(s -> assets.lockById(m.getId()).ifPresent(x -> {
                if (x.getState() != MediaAsset.State.UPLOAD_PENDENTE) return;
                x.expired();
                if (deleted) x.originalDeleted(now);
                assets.save(x);
            }));
        }
        for (MediaAsset m : assets.findByOriginalDeletedAtIsNullAndCreatedAtBefore(now.minus(ORIGINAL_MAX_AGE))) {
            if (!deleteQuietly(storage.buckets().mediaQuarantine(), m.getQuarantineKey())) continue;
            tx.executeWithoutResult(s -> assets.findById(m.getId()).ifPresent(x -> {
                x.originalDeleted(now);
                assets.save(x);
            }));
            audit.record(AuditRecord.of("media.original_deleted").resource("media", m.getId()).detail("reason", "24h"));
        }
        recoverStuck(now);
    }

    void recoverStuck(Instant now) {
        for (MediaAsset m : assets.findByStateAndStateChangedAtBefore(MediaAsset.State.QUARENTENA, now.minus(QUEUED_STALE))) {
            workers.submit(() -> process(m.getId()));
        }
        for (MediaAsset m : assets.findByStateAndStateChangedAtBefore(MediaAsset.State.PROCESSANDO, now.minus(PROCESSING_STALE))) {
            boolean requeued = Boolean.TRUE.equals(tx.execute(s -> {
                MediaAsset x = assets.lockById(m.getId()).orElseThrow();
                if (x.getState() != MediaAsset.State.PROCESSANDO || x.getStateChangedAt().isAfter(now.minus(PROCESSING_STALE))) return false;
                if (x.getProcessingAttempts() >= MAX_ATTEMPTS) return null;
                x.requeue();
                assets.save(x);
                return true;
            }));
            if (requeued) {
                workers.submit(() -> process(m.getId()));
            } else if (m.getProcessingAttempts() >= MAX_ATTEMPTS) {
                endWithError(m.getId(), m, false, "A anonimização foi interrompida " + MAX_ATTEMPTS + " vezes. Nada foi publicado; envie de novo.");
            }
        }
    }

    private MediaAsset owned(UUID userId, UUID id) {
        return assets.findById(id).filter(m -> m.getOwnerId().equals(userId)).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A mídia não existe ou não é sua."));
    }

    private static void requireReview(MediaAsset a, int previewVersion) {
        if (a.getState() != MediaAsset.State.AGUARDANDO_REVISAO) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "midia-estado", "Mídia fora da revisão", "A mídia está em " + a.getState() + ".");
        }
        if (previewVersion != a.getPreviewVersion()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "previa-desatualizada", "Prévia desatualizada",
                    "A prévia mudou desde que você a abriu. Revise a versão atual antes de continuar.");
        }
    }

    private DeidentificationReport readReport(MediaAsset a) {
        return json.readValue(a.getReport(), DeidentificationReport.class);
    }

    private Map<String, Object> view(MediaAsset a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId());
        v.put("kind", a.getKind().name());
        v.put("state", a.getState().name());
        v.put("previewVersion", a.getPreviewVersion());
        v.put("width", a.getWidth());
        v.put("height", a.getHeight());
        v.put("dermatology", a.isDermatology());
        v.put("reason", a.getReason() == null ? "" : a.getReason());
        v.put("report", a.getReport() == null ? null : json.readTree(a.getReport()));
        v.put("postId", a.getPostId() == null ? "" : a.getPostId().toString());
        return v;
    }

    /** true se o S3 confirmou (apagar o que não existe também conta); false deixa para o job tentar de novo. */
    private boolean deleteQuietly(String bucket, String key) {
        try {
            objects.delete(bucket, key);
            return true;
        } catch (RuntimeException e) {
            log.warn("Não apagou {}/{}: {} (o job de 24 h tenta de novo)", bucket, key, e.toString());
            return false;
        }
    }
}
