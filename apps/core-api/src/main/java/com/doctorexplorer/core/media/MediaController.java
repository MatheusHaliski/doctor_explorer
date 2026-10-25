package com.doctorexplorer.core.media;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.identity.web.SessionSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF05 · /api/v1/media: pedir envio, concluir, revisar a prévia, cobrir áreas, aprovar ou descartar. Exige selo VERIFIED. */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    public record UploadRequest(String contentType, Long size, Boolean dermatology, Boolean blurFaces) {}

    public record RedactionRequest(Integer previewVersion, List<MediaService.NormRegion> regions) {}

    public record ApproveRequest(Integer previewVersion, Boolean reviewed) {}

    private final MediaService media;
    private final AccessService access;
    private final SessionSupport sessions;

    public MediaController(MediaService media, AccessService access, SessionSupport sessions) {
        this.media = media;
        this.access = access;
        this.sessions = sessions;
    }

    @PostMapping("/uploads")
    ResponseEntity<Map<String, Object>> upload(@RequestBody UploadRequest r) {
        UUID userId = access.requireVerifiedSeal().userId();
        MediaService.Upload u = media.requestUpload(userId, r.contentType(), r.size() == null ? 0 : r.size(), Boolean.TRUE.equals(r.dermatology()), r.blurFaces());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", u.asset().getId());
        v.put("state", u.asset().getState().name());
        v.put("uploadUrl", u.uploadUrl().toString());
        v.put("method", "PUT");
        v.put("headers", Map.of("Content-Type", u.asset().getDeclaredType()));
        v.put("expiresAt", u.asset().getUploadExpiresAt().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(v);
    }

    @PostMapping("/{id}/complete")
    ResponseEntity<Map<String, Object>> complete(@PathVariable UUID id) {
        MediaAsset a = media.complete(sessions.require().userId(), id);
        return ResponseEntity.accepted().body(Map.of("id", a.getId(), "state", a.getState().name()));
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return media.view(sessions.require().userId(), id);
    }

    @PostMapping("/{id}/redactions")
    Map<String, Object> redact(@PathVariable UUID id, @RequestBody RedactionRequest r) {
        return media.redact(access.requireVerifiedSeal().userId(), id, r.previewVersion() == null ? -1 : r.previewVersion(), r.regions());
    }

    @PostMapping("/{id}/approve")
    Map<String, Object> approve(@PathVariable UUID id, @RequestBody ApproveRequest r) {
        return media.approve(access.requireVerifiedSeal().userId(), id, r.previewVersion() == null ? -1 : r.previewVersion(), Boolean.TRUE.equals(r.reviewed()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> discard(@PathVariable UUID id) {
        media.discard(sessions.require().userId(), id);
        return ResponseEntity.noContent().build();
    }
}
