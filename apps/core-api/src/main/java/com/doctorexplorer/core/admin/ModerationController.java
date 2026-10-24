package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.community.domain.GroupMembership;
import com.doctorexplorer.core.identity.web.SessionSupport;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF02/RF04/RF12 · /api/v1/admin/moderation: papel MODERATOR ou ADMIN + 2FA ativo (regra em SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/moderation")
public class ModerationController {

    public record DecisionRequest(String reason) {}

    private final ModerationService moderation;
    private final SessionSupport sessions;

    public ModerationController(ModerationService moderation, SessionSupport sessions) {
        this.moderation = moderation;
        this.sessions = sessions;
    }

    @GetMapping("/queue")
    Map<String, Object> queue() {
        return moderation.queue();
    }

    @PostMapping("/seals/{userId}/approve")
    Map<String, Object> approveSeal(@PathVariable UUID userId, @RequestBody(required = false) DecisionRequest req) {
        return moderation.decideSeal(sessions.require().userId(), userId, true, req == null ? null : req.reason());
    }

    @PostMapping("/seals/{userId}/reject")
    Map<String, Object> rejectSeal(@PathVariable UUID userId, @RequestBody(required = false) DecisionRequest req) {
        return moderation.decideSeal(sessions.require().userId(), userId, false, req == null ? null : req.reason());
    }

    @PostMapping("/groups/{code}/members/{userId}/approve")
    Map<String, Object> approveGroup(@PathVariable String code, @PathVariable UUID userId) {
        GroupMembership m = moderation.approveGroupRequest(code, userId);
        return Map.of("userId", userId, "specialtyCode", code.toUpperCase(Locale.ROOT), "state", m.getState().name(), "approvedBy", sessions.require().userId());
    }
}
