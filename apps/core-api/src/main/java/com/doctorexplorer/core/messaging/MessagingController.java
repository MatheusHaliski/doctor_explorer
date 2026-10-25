package com.doctorexplorer.core.messaging;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.messaging.MessagingService.CommitInput;
import com.doctorexplorer.core.messaging.MessagingService.ConversationView;
import com.doctorexplorer.core.messaging.MessagingService.DeviceView;
import com.doctorexplorer.core.messaging.MessagingService.MessageInput;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF06 · /api/v1/messaging: aparelhos (diretório de chaves públicas), KeyPackages, conversas MLS, commits e mensagens
 * cifradas, anexos cifrados e denúncia. Conversar exige selo VERIFIED; ler o próprio estado (aparelhos, conversas)
 * exige só sessão ativa, para quem perdeu o selo ver por que não envia.
 */
@RestController
public class MessagingController {

    public record NewDevice(String name, String signatureKey, String recoveryVerifier) {}

    public record Approval(UUID approverDeviceId, String signature) {}

    public record Recovery(String verifier) {}

    public record KeyPackages(List<String> keyPackages) {}

    public record NewConversation(String kind, List<UUID> memberIds, String title, Integer retentionDays, UUID deviceId) {}

    public record Claim(List<UUID> deviceIds) {}

    public record Read(Long seq) {}

    public record Settings(Integer retentionDays) {}

    public record Members(List<UUID> userIds) {}

    public record Attachment(Long size) {}

    public record Report(UUID reportedUserId, String reason, String excerpt) {}

    public record Resolution(String resolution) {}

    private static final String BASE = "/api/v1/messaging";

    private final MessagingService messaging;
    private final AccessService access;

    public MessagingController(MessagingService messaging, AccessService access) {
        this.messaging = messaging;
        this.access = access;
    }

    private UUID verified() {
        return access.requireVerifiedSeal().userId();
    }

    private UUID active() {
        return access.requireActive().userId();
    }

    // aparelhos

    @GetMapping(BASE + "/devices")
    Map<String, Object> devices() {
        return Map.of("items", messaging.myDevices(active()));
    }

    @PostMapping(BASE + "/devices")
    ResponseEntity<DeviceView> register(@RequestBody NewDevice body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messaging.registerDevice(verified(), body.name(), body.signatureKey(), body.recoveryVerifier()));
    }

    @GetMapping(BASE + "/devices/{id}/public")
    DeviceView publicDevice(@PathVariable UUID id) {
        verified();
        return messaging.publicDevice(id);
    }

    @PostMapping(BASE + "/devices/{id}/approve")
    DeviceView approve(@PathVariable UUID id, @RequestBody Approval body) {
        return messaging.approveDevice(verified(), id, body.approverDeviceId(), body.signature());
    }

    @PostMapping(BASE + "/devices/{id}/recover")
    DeviceView recover(@PathVariable UUID id, @RequestBody Recovery body) {
        return messaging.recoverDevice(verified(), id, body.verifier());
    }

    @DeleteMapping(BASE + "/devices/{id}")
    ResponseEntity<Void> revoke(@PathVariable UUID id) {
        messaging.revokeDevice(active(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(BASE + "/devices/{id}/key-packages")
    Map<String, Object> publish(@PathVariable UUID id, @RequestBody KeyPackages body) {
        return Map.of("free", messaging.publishKeyPackages(verified(), id, body.keyPackages()));
    }

    @GetMapping(BASE + "/devices/{id}/welcomes")
    Map<String, Object> welcomes(@PathVariable UUID id) {
        return Map.of("items", messaging.welcomes(active(), id));
    }

    @DeleteMapping(BASE + "/devices/{id}/welcomes/{welcomeId}")
    ResponseEntity<Void> ack(@PathVariable UUID id, @PathVariable UUID welcomeId) {
        messaging.ackWelcome(active(), id, welcomeId);
        return ResponseEntity.noContent().build();
    }

    // conversas

    @GetMapping(BASE + "/contacts")
    Map<String, Object> contacts(@RequestParam(defaultValue = "") String q) {
        return Map.of("items", messaging.contacts(verified(), q));
    }

    @GetMapping(BASE + "/conversations")
    Map<String, Object> list() {
        return Map.of("items", messaging.list(active()));
    }

    @GetMapping(BASE + "/unread")
    Map<String, Object> unread() {
        return Map.of("conversations", messaging.unreadCount(active()));
    }

    @PostMapping(BASE + "/conversations")
    ResponseEntity<ConversationView> create(@RequestBody NewConversation body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messaging.create(verified(), body.kind(), body.memberIds(), body.title(), body.retentionDays(), body.deviceId()));
    }

    @GetMapping(BASE + "/conversations/{id}")
    ConversationView get(@PathVariable UUID id) {
        return messaging.get(active(), id);
    }

    @PatchMapping(BASE + "/conversations/{id}")
    ConversationView settings(@PathVariable UUID id, @RequestBody Settings body) {
        return messaging.setRetention(verified(), id, body.retentionDays());
    }

    @PostMapping(BASE + "/conversations/{id}/key-packages")
    Map<String, Object> claim(@PathVariable UUID id, @RequestBody Claim body) {
        return Map.of("items", messaging.claimKeyPackages(verified(), id, body.deviceIds()));
    }

    @PostMapping(BASE + "/conversations/{id}/commits")
    Map<String, Object> commit(@PathVariable UUID id, @RequestBody CommitInput body) {
        return messaging.commit(verified(), id, body);
    }

    @PostMapping(BASE + "/conversations/{id}/messages")
    Map<String, Object> send(@PathVariable UUID id, @RequestBody MessageInput body) {
        return messaging.send(verified(), id, body);
    }

    @GetMapping(BASE + "/conversations/{id}/messages")
    Map<String, Object> messages(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "200") int limit) {
        return Map.of("items", messaging.messages(active(), id, after, limit));
    }

    @PostMapping(BASE + "/conversations/{id}/read")
    ResponseEntity<Void> read(@PathVariable UUID id, @RequestBody Read body) {
        messaging.markRead(active(), id, body.seq() == null ? 0 : body.seq());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(BASE + "/conversations/{id}/members")
    ConversationView addMembers(@PathVariable UUID id, @RequestBody Members body) {
        return messaging.addMembers(verified(), id, body.userIds());
    }

    @DeleteMapping(BASE + "/conversations/{id}/members/{userId}")
    ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        messaging.removeMember(active(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(BASE + "/conversations/{id}/attachments")
    ResponseEntity<Map<String, Object>> attachment(@PathVariable UUID id, @RequestBody Attachment body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messaging.attachmentUpload(verified(), id, body.size()));
    }

    @GetMapping(BASE + "/conversations/{id}/attachments/{attachmentId}")
    Map<String, Object> download(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return messaging.attachmentDownload(active(), id, attachmentId);
    }

    @PostMapping(BASE + "/conversations/{id}/reports")
    ResponseEntity<Map<String, Object>> report(@PathVariable UUID id, @RequestBody Report body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messaging.report(active(), id, body.reportedUserId(), body.reason(), body.excerpt()));
    }

    // moderação (MODERATOR/ADMIN + 2FA pela SecurityConfig)

    @GetMapping("/api/v1/admin/moderation/message-reports")
    Map<String, Object> reports() {
        return Map.of("items", messaging.openReports());
    }

    @PostMapping("/api/v1/admin/moderation/message-reports/{id}/resolve")
    ResponseEntity<Void> resolve(@PathVariable UUID id, @RequestBody Resolution body) {
        messaging.resolveReport(access.requireActive().userId(), id, body.resolution());
        return ResponseEntity.noContent().build();
    }
}
