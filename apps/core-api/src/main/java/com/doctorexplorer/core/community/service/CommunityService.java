package com.doctorexplorer.core.community.service;

import com.doctorexplorer.core.community.domain.CasePost;
import com.doctorexplorer.core.community.domain.GroupMembership;
import com.doctorexplorer.core.community.domain.PostComment;
import com.doctorexplorer.core.community.domain.SpecialtyGroup;
import com.doctorexplorer.core.community.repo.CasePosts;
import com.doctorexplorer.core.community.repo.GroupMemberships;
import com.doctorexplorer.core.community.repo.PostComments;
import com.doctorexplorer.core.community.repo.SpecialtyGroups;
import com.doctorexplorer.core.credentialing.repo.SpecialtyClaims;
import com.doctorexplorer.core.institution.domain.InstitutionMembership;
import com.doctorexplorer.core.institution.service.DirectoryService;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF04 · Grupos por especialidade, publicação anonimizada, pareceres e feed segmentado por classificações. */
@Service
public class CommunityService {

    public record NewPost(String specialtyCode, CasePost.Kind kind, CasePost.Audience audience, String title,
            String complaint, String history, String exam, String workup, String question, List<String> tags) {}

    /** Filtros do feed: cada eixo aceita um valor ou nulo (= todos). */
    public record FeedFilter(CasePost.Kind kind, Set<String> specialties, String origin, CasePost.FeedState state, boolean unansweredOnly) {}

    public static final int PAGE = 50;

    private final SpecialtyGroups groups;
    private final GroupMemberships memberships;
    private final CasePosts posts;
    private final PostComments comments;
    private final SpecialtyClaims claims;
    private final IdentifierDetector detector;
    private final DirectoryService directory;

    public CommunityService(SpecialtyGroups groups, GroupMemberships memberships, CasePosts posts, PostComments comments,
            SpecialtyClaims claims, IdentifierDetector detector, DirectoryService directory) {
        this.groups = groups;
        this.memberships = memberships;
        this.posts = posts;
        this.comments = comments;
        this.claims = claims;
        this.detector = detector;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyGroup> listGroups() {
        return groups.findAll().stream().sorted((a, b) -> a.getName().compareTo(b.getName())).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, GroupMembership.State> membershipsOf(UUID userId) {
        return memberships.findAll().stream().filter(m -> m.getUserId().equals(userId))
                .collect(Collectors.toMap(GroupMembership::getGroupId, GroupMembership::getState));
    }

    /** RQE confirmado para a especialidade → ACTIVE na hora; senão → REQUESTED, à espera de aprovação. */
    @Transactional
    public GroupMembership join(UUID userId, String specialtyCode) {
        SpecialtyGroup g = groupByCode(specialtyCode);
        Optional<GroupMembership> existing = memberships.findById(new GroupMembership.Key(g.getId(), userId));
        if (existing.isPresent() && existing.get().getState() == GroupMembership.State.ACTIVE) return existing.get();
        boolean rqeConfirmed = g.getJoinPolicy() == SpecialtyGroup.JoinPolicy.RQE_OR_APPROVAL
                && claims.findByUserId(userId).stream().anyMatch(c -> c.isConfirmed() && c.getSpecialtyCode().equals(g.getSpecialtyCode()));
        GroupMembership m = existing.orElseGet(() -> new GroupMembership(g.getId(), userId, GroupMembership.State.REQUESTED));
        if (rqeConfirmed) m.approve();
        return memberships.save(m);
    }

    /** Aprovação por moderador do grupo (ou moderador da plataforma, checado no controller). */
    @Transactional
    public GroupMembership approve(String specialtyCode, UUID userId) {
        SpecialtyGroup g = groupByCode(specialtyCode);
        GroupMembership m = memberships.findById(new GroupMembership.Key(g.getId(), userId)).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Pedido não encontrado", "Não há pedido de entrada desse usuário."));
        m.approve();
        return memberships.save(m);
    }

    @Transactional
    public CasePost publish(UUID authorId, NewPost n) {
        List<FieldProblem> problems = new ArrayList<>();
        if (n.title() == null || n.title().trim().length() < 8 || n.title().trim().length() > 140) {
            problems.add(new FieldProblem("title", "O título precisa ter de 8 a 140 caracteres."));
        }
        if (n.kind() == null) problems.add(new FieldProblem("kind", "Informe o tipo: CASE, OPINION_REQUEST ou EDUCATION."));
        if (n.kind() == CasePost.Kind.PROTOCOL || n.kind() == CasePost.Kind.NOTICE) {
            problems.add(new FieldProblem("kind", "Protocolos e comunicados são publicados pela instituição (RF11)."));
        }
        if ((n.kind() == CasePost.Kind.CASE || n.kind() == CasePost.Kind.OPINION_REQUEST) && isBlank(n.question())) {
            problems.add(new FieldProblem("question", "Diga o que você quer saber dos colegas."));
        }
        SpecialtyGroup g = null;
        try {
            g = groupByCode(n.specialtyCode());
        } catch (ApiProblemException e) {
            problems.add(new FieldProblem("specialtyCode", e.getMessage()));
        }
        // anonimização obrigatória: qualquer identificador bloqueia, com o trecho destacado
        String[][] fields = {{"title", n.title()}, {"complaint", n.complaint()}, {"history", n.history()}, {"exam", n.exam()},
                {"workup", n.workup()}, {"question", n.question()}};
        for (String[] field : fields) {
            for (IdentifierDetector.Match m : detector.scan(field[1])) {
                problems.add(new FieldProblem(field[0], "Possível identificador (" + m.type() + ") em \"" + m.excerpt() + "\". " + m.hint()));
            }
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        CasePost.Audience audience = n.audience() == null ? CasePost.Audience.GROUP : n.audience();
        if (audience == CasePost.Audience.GROUP && !isActiveMember(authorId, g.getId())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "nao-membro", "Entre no grupo primeiro",
                    "Para publicar no grupo de " + g.getName() + " é preciso ser membro. Peça entrada em /api/v1/groups/" + g.getSpecialtyCode() + "/join.");
        }
        CasePost p = new CasePost(g.getId(), authorId, n.kind(), audience, n.title(),
                new CasePost.Sections(n.complaint(), n.history(), n.exam(), n.workup(), n.question()), n.tags());
        return posts.save(p);
    }

    /** RF11 · Protocolo ou comunicado da instituição: gestor com papel que publica; vai ao feed dos vinculados (origem "instituição"). */
    @Transactional
    public CasePost publishInstitutional(UUID authorId, UUID institutionId, CasePost.Kind kind, String specialtyCode, String title, String body, List<String> tags) {
        InstitutionMembership.Role role = directory.effectiveRole(authorId, institutionId).orElseThrow(this::notFound);
        if (!role.canPublishInstitutional()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem permissão para publicar pela instituição",
                    "Seu papel " + role.name() + " não publica protocolos ou comunicados.");
        }
        if (kind != CasePost.Kind.PROTOCOL && kind != CasePost.Kind.NOTICE) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "tipo", "Tipo inválido", "A instituição publica PROTOCOL ou NOTICE.");
        }
        List<FieldProblem> problems = new ArrayList<>();
        if (title == null || title.trim().length() < 8) problems.add(new FieldProblem("title", "O título precisa ter pelo menos 8 caracteres."));
        for (IdentifierDetector.Match m : detector.scan(body)) problems.add(new FieldProblem("body", "Possível identificador (" + m.type() + ") em \"" + m.excerpt() + "\". " + m.hint()));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        SpecialtyGroup g = groupByCode(specialtyCode == null ? "CLIN" : specialtyCode);
        CasePost p = new CasePost(g.getId(), authorId, kind, CasePost.Audience.INSTITUTION, title,
                new CasePost.Sections(body, null, null, null, null), tags);
        p.setInstitutionId(institutionId);
        return posts.save(p);
    }

    @Transactional(readOnly = true)
    public CasePost view(UUID viewerId, UUID postId) {
        CasePost p = posts.findById(postId).filter(CasePost::isVisible).orElseThrow(this::notFound);
        if (p.getAudience() == CasePost.Audience.GROUP && !isActiveMember(viewerId, p.getGroupId()) && !p.getAuthorId().equals(viewerId)) {
            throw notFound();
        }
        if (p.getAudience() == CasePost.Audience.INSTITUTION && !p.getAuthorId().equals(viewerId)
                && directory.membershipsOf(viewerId).stream().noneMatch(m -> m.getInstitutionId().equals(p.getInstitutionId()))) {
            throw notFound();
        }
        return p;
    }

    @Transactional
    public PostComment comment(UUID authorId, UUID postId, String body, boolean opinion) {
        CasePost p = view(authorId, postId);
        List<FieldProblem> problems = new ArrayList<>();
        if (isBlank(body) || body.trim().length() > 5000) problems.add(new FieldProblem("body", "Escreva de 1 a 5000 caracteres."));
        for (IdentifierDetector.Match m : detector.scan(body)) {
            problems.add(new FieldProblem("body", "Possível identificador (" + m.type() + ") em \"" + m.excerpt() + "\". " + m.hint()));
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        if (p.getState() == CasePost.State.RESOLVED && opinion) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "caso-resolvido", "Caso já resolvido", "O autor marcou este caso como resolvido; você ainda pode comentar, mas não como parecer.");
        }
        PostComment c = comments.save(new PostComment(postId, authorId, body, opinion));
        p.registerComment(opinion);
        posts.save(p);
        return c;
    }

    @Transactional(readOnly = true)
    public List<PostComment> comments(UUID viewerId, UUID postId) {
        view(viewerId, postId);
        return comments.findByPostIdOrderByCreatedAtAsc(postId).stream().filter(c -> !c.isHidden()).toList();
    }

    @Transactional
    public CasePost resolve(UUID userId, UUID postId) {
        CasePost p = posts.findById(postId).orElseThrow(this::notFound);
        if (!p.getAuthorId().equals(userId)) throw notFound(); // dono ou 404
        p.resolve();
        return posts.save(p);
    }

    @Transactional
    public CasePost delete(UUID userId, UUID postId) {
        CasePost p = posts.findById(postId).orElseThrow(this::notFound);
        if (!p.getAuthorId().equals(userId)) throw notFound();
        p.delete();
        return posts.save(p);
    }

    /** Moderação (RF12/RF13): oculta com motivo; fica na auditoria. */
    @Transactional
    public CasePost hide(UUID postId, String reason) {
        CasePost p = posts.findById(postId).orElseThrow(this::notFound);
        p.hide(reason == null || reason.isBlank() ? "moderação" : reason.trim());
        return posts.save(p);
    }

    /** Feed: mais recente primeiro, sem ranking por engajamento; filtros combináveis por eixo. */
    @Transactional(readOnly = true)
    public List<CasePost> feed(UUID viewerId, FeedFilter f) {
        Set<UUID> myGroups = memberships.findByUserIdAndState(viewerId, GroupMembership.State.ACTIVE).stream()
                .map(GroupMembership::getGroupId).collect(Collectors.toSet());
        Map<UUID, String> codeByGroup = groups.findAll().stream().collect(Collectors.toMap(SpecialtyGroup::getId, SpecialtyGroup::getSpecialtyCode));
        Set<UUID> myInstitutions = directory.membershipsOf(viewerId).stream().map(InstitutionMembership::getInstitutionId).collect(Collectors.toSet());
        return posts.feed(myGroups.isEmpty() ? List.of(new UUID(0, 0)) : myGroups, myInstitutions.isEmpty() ? List.of(new UUID(0, 0)) : myInstitutions, PageRequest.of(0, PAGE)).stream()
                .filter(p -> f.kind() == null || p.getKind() == f.kind())
                .filter(p -> f.specialties() == null || f.specialties().isEmpty() || f.specialties().contains(codeByGroup.get(p.getGroupId())))
                .filter(p -> f.origin() == null || switch (f.origin()) {
                    case "grupo" -> myGroups.contains(p.getGroupId()) && p.getAudience() == CasePost.Audience.GROUP;
                    case "rede" -> p.getAudience() == CasePost.Audience.NETWORK;
                    case "instituicao" -> p.getAudience() == CasePost.Audience.INSTITUTION;
                    default -> true;
                })
                .filter(p -> f.state() == null || p.feedState() == f.state())
                .filter(p -> !f.unansweredOnly() || p.feedState() == CasePost.FeedState.SEM_RESPOSTA)
                .toList();
    }

    public String specialtyCodeOf(UUID groupId) {
        return groups.findById(groupId).map(SpecialtyGroup::getSpecialtyCode).orElse("?");
    }

    public boolean isActiveMember(UUID userId, UUID groupId) {
        return memberships.findById(new GroupMembership.Key(groupId, userId)).map(m -> m.getState() == GroupMembership.State.ACTIVE).orElse(false);
    }

    private SpecialtyGroup groupByCode(String code) {
        if (code == null) throw new ApiProblemException(HttpStatus.NOT_FOUND, "grupo", "Grupo não encontrado", "Informe a especialidade do grupo.");
        return groups.findBySpecialtyCode(code.trim().toUpperCase(Locale.ROOT)).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "grupo", "Grupo não encontrado", "Não existe grupo para a especialidade " + code + "."));
    }

    private ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A publicação não existe ou você não tem acesso.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
