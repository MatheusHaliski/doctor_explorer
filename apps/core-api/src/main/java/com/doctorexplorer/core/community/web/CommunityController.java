package com.doctorexplorer.core.community.web;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.community.domain.CasePost;
import com.doctorexplorer.core.community.domain.GroupMembership;
import com.doctorexplorer.core.community.domain.PostComment;
import com.doctorexplorer.core.community.service.CommunityService;
import com.doctorexplorer.core.community.service.CommunityService.FeedFilter;
import com.doctorexplorer.core.community.service.CommunityService.NewPost;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.media.MediaAsset;
import com.doctorexplorer.core.media.MediaService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF04 · /api/v1/groups, /api/v1/posts e /api/v1/feed. Tudo exige selo VERIFIED (matriz RF12).
 * O feed é segmentado por quatro eixos: kind (tipo), specialty (especialidade), origin (grupo|rede) e
 * state (SEM_RESPOSTA|EM_DISCUSSAO|RESOLVIDO), mais unanswered=true.
 */
@RestController
@RequestMapping("/api/v1")
public class CommunityController {

    public record PostRequest(String specialtyCode, String kind, String audience, String title, String complaint,
            String history, String exam, String workup, String question, List<String> tags, List<UUID> mediaIds) {}

    public record CommentRequest(String body, boolean opinion) {}

    public record HideRequest(String reason) {}

    public record InstitutionalPostRequest(String kind, String specialtyCode, String title, String body, List<String> tags) {}

    private final CommunityService community;
    private final AccessService access;
    private final SessionSupport sessions;
    private final UserAccounts accounts;
    private final MediaService media;

    public CommunityController(CommunityService community, AccessService access, SessionSupport sessions, UserAccounts accounts, MediaService media) {
        this.community = community;
        this.access = access;
        this.sessions = sessions;
        this.accounts = accounts;
        this.media = media;
    }

    @GetMapping("/groups")
    Map<String, Object> groups() {
        UUID userId = access.requireVerifiedSealOrModerator().userId();
        Map<UUID, GroupMembership.State> mine = community.membershipsOf(userId);
        return Map.of("groups", community.listGroups().stream().map(g -> {
            Map<String, Object> v = new LinkedHashMap<>(); // ordem estável dos campos no JSON
            v.put("specialtyCode", g.getSpecialtyCode());
            v.put("name", g.getName());
            v.put("joinPolicy", g.getJoinPolicy().name());
            v.put("membership", mine.containsKey(g.getId()) ? mine.get(g.getId()).name() : "NONE");
            return v;
        }).toList());
    }

    @PostMapping("/groups/{code}/join")
    Map<String, Object> join(@PathVariable String code) {
        UUID userId = access.requireVerifiedSeal().userId();
        GroupMembership m = community.join(userId, code);
        return Map.of("specialtyCode", code.toUpperCase(Locale.ROOT), "state", m.getState().name(),
                "note", m.getState() == GroupMembership.State.ACTIVE ? "Você entrou no grupo." : "Pedido enviado ao moderador do grupo.");
    }

    @PostMapping("/groups/{code}/members/{userId}/approve")
    Map<String, Object> approve(@PathVariable String code, @PathVariable UUID userId) {
        var p = access.requireVerifiedSealOrModerator();
        if (!access.isPlatformModerator()) {
            throw new com.doctorexplorer.core.web.ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Só moderadores aprovam entrada",
                    "Peça a um moderador do grupo ou da plataforma.");
        }
        GroupMembership m = community.approve(code, userId);
        return Map.of("userId", userId, "state", m.getState().name(), "approvedBy", p.userId());
    }

    @GetMapping("/feed")
    Map<String, Object> feed(@RequestParam(required = false) String kind, @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String origin, @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean unanswered) {
        UUID userId = access.requireVerifiedSealOrModerator().userId();
        Set<String> specialties = specialty == null || specialty.isBlank() ? Set.of()
                : Arrays.stream(specialty.split(",")).map(s -> s.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        FeedFilter f = new FeedFilter(parse(CasePost.Kind.class, kind), specialties, origin == null ? null : origin.toLowerCase(Locale.ROOT),
                parse(CasePost.FeedState.class, state), unanswered);
        List<CasePost> items = community.feed(userId, f);
        return Map.of("items", items.stream().map(this::postView).toList(), "count", items.size(),
                "filters", Map.of("kind", kind == null ? "" : kind, "specialty", specialties, "origin", origin == null ? "" : origin,
                        "state", state == null ? "" : state, "unanswered", unanswered));
    }

    @PostMapping("/posts")
    ResponseEntity<Map<String, Object>> publish(@RequestBody PostRequest req) {
        UUID userId = access.requireVerifiedSeal().userId();
        // RF05: só entram imagens do autor já anonimizadas e aprovadas por ele
        List<MediaAsset> attached = media.requireAttachable(userId, req.mediaIds());
        CasePost p = community.publish(userId, new NewPost(req.specialtyCode(), parse(CasePost.Kind.class, req.kind()),
                parse(CasePost.Audience.class, req.audience()), req.title() == null ? "" : req.title(), req.complaint(), req.history(),
                req.exam(), req.workup(), req.question(), req.tags()));
        media.attach(p.getId(), attached);
        return ResponseEntity.status(HttpStatus.CREATED).body(postView(p));
    }

    @GetMapping("/posts/{id}")
    Map<String, Object> view(@PathVariable UUID id) {
        UUID userId = access.requireVerifiedSealOrModerator().userId();
        CasePost p = community.view(userId, id);
        Map<String, Object> v = new LinkedHashMap<>(postView(p));
        v.put("sections", Map.of("complaint", n(p.sections().complaint()), "history", n(p.sections().history()), "exam", n(p.sections().exam()),
                "workup", n(p.sections().workup()), "question", n(p.sections().question())));
        v.put("media", media.forPost(id)); // URLs de leitura curtas só depois de a comunidade liberar a publicação
        v.put("comments", community.comments(userId, id).stream().map(this::commentView).toList());
        v.put("canRecordInEhr", p.getAuthorId().equals(userId)); // "Registrar no prontuário" só para o autor (RF08)
        return v;
    }

    @PostMapping("/posts/{id}/comments")
    ResponseEntity<Map<String, Object>> comment(@PathVariable UUID id, @RequestBody CommentRequest req) {
        UUID userId = access.requireVerifiedSeal().userId();
        PostComment c = community.comment(userId, id, req.body(), req.opinion());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentView(c));
    }

    @PostMapping("/posts/{id}/resolve")
    Map<String, Object> resolve(@PathVariable UUID id) {
        return postView(community.resolve(access.requireVerifiedSeal().userId(), id));
    }

    @DeleteMapping("/posts/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        community.delete(access.requireVerifiedSeal().userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** RF11 · Protocolo/comunicado publicado pela instituição (gestor com papel que publica). */
    @PostMapping("/institutions/{institutionId}/posts")
    ResponseEntity<Map<String, Object>> publishInstitutional(@PathVariable UUID institutionId, @RequestBody InstitutionalPostRequest req) {
        UUID userId = access.requireActive().userId();
        CasePost p = community.publishInstitutional(userId, institutionId, parse(CasePost.Kind.class, req.kind()), req.specialtyCode(),
                req.title() == null ? "" : req.title(), req.body(), req.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(postView(p));
    }

    /** Moderação: rota /admin (papel + 2FA via SecurityConfig). */
    @PostMapping("/admin/posts/{id}/hide")
    Map<String, Object> hide(@PathVariable UUID id, @RequestBody HideRequest req) {
        return postView(community.hide(id, req.reason()));
    }

    private Map<String, Object> postView(CasePost p) {
        var author = accounts.findById(p.getAuthorId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("kind", p.getKind().name());
        m.put("specialty", community.specialtyCodeOf(p.getGroupId()));
        m.put("origin", switch (p.getAudience()) { case NETWORK -> "rede"; case GROUP -> "grupo"; case INSTITUTION -> "instituicao"; });
        m.put("state", p.feedState().name());
        m.put("title", p.getTitle());
        m.put("summary", n(p.sections().question()).isEmpty() ? n(p.sections().complaint()) : n(p.sections().question()));
        m.put("tags", p.tagList());
        m.put("comments", p.getCommentCount());
        m.put("opinions", p.getOpinionCount());
        m.put("mediaCount", media.countForPost(p.getId()));
        m.put("authorId", p.getAuthorId());
        m.put("authorName", author.map(a -> a.getDisplayName()).orElse("Conta excluída"));
        m.put("createdAt", p.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> commentView(PostComment c) {
        return Map.of("id", c.getId(), "authorId", c.getAuthorId(), "authorName", accounts.findById(c.getAuthorId()).map(a -> a.getDisplayName()).orElse("Conta excluída"),
                "body", c.getBody(), "opinion", c.isOpinion(), "createdAt", c.getCreatedAt().toString());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
