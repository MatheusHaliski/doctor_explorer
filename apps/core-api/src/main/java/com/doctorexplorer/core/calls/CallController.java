package com.doctorexplorer.core.calls;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.calls.CallService.CallView;
import com.doctorexplorer.core.calls.CallService.Joined;
import com.doctorexplorer.core.identity.web.SessionPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF07 · /api/v1/calls: chamar, atender, recusar, sair e ver quem está chamando. Exige selo VERIFIED. */
@RestController
@RequestMapping("/api/v1/calls")
public class CallController {

    public record Start(UUID conversationId, UUID deviceId) {}

    public record Join(UUID deviceId) {}

    public record Leave(String quality, Boolean screenShared) {}

    private final CallService calls;
    private final AccessService access;

    public CallController(CallService calls, AccessService access) {
        this.calls = calls;
        this.access = access;
    }

    @PostMapping
    ResponseEntity<Joined> start(@RequestBody Start body) {
        SessionPrincipal p = access.requireVerifiedSeal();
        return ResponseEntity.status(HttpStatus.CREATED).body(calls.start(p.userId(), p.displayName(), body.conversationId(), body.deviceId()));
    }

    @GetMapping("/incoming")
    Map<String, Object> incoming() {
        return Map.of("items", calls.incoming(access.requireActive().userId()));
    }

    @GetMapping("/{id}")
    CallView get(@PathVariable UUID id) {
        return calls.get(access.requireVerifiedSeal().userId(), id);
    }

    @PostMapping("/{id}/join")
    Joined join(@PathVariable UUID id, @RequestBody Join body) {
        SessionPrincipal p = access.requireVerifiedSeal();
        return calls.join(p.userId(), p.displayName(), id, body.deviceId());
    }

    @PostMapping("/{id}/decline")
    CallView decline(@PathVariable UUID id) {
        return calls.decline(access.requireVerifiedSeal().userId(), id);
    }

    @PostMapping("/{id}/leave")
    CallView leave(@PathVariable UUID id, @RequestBody Leave body) {
        return calls.leave(access.requireActive().userId(), id, body.quality(), body.screenShared());
    }
}
