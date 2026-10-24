package com.doctorexplorer.core.institution.service;

import com.doctorexplorer.core.common.Hashing;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.institution.domain.Institution;
import com.doctorexplorer.core.institution.domain.InstitutionCheck;
import com.doctorexplorer.core.institution.domain.InstitutionMembership;
import com.doctorexplorer.core.institution.domain.InstitutionMembership.Role;
import com.doctorexplorer.core.institution.domain.InstitutionMembership.State;
import com.doctorexplorer.core.institution.repo.InstitutionChecks;
import com.doctorexplorer.core.institution.repo.InstitutionMemberships;
import com.doctorexplorer.core.institution.repo.Institutions;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnesPort;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnesRecord;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnpjPort;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnpjRecord;
import com.doctorexplorer.core.institution.service.RegistryPorts.RegistryUnavailableException;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF11 · Cadastro com CNPJ+CNES conferidos, hierarquia, vínculos aceitos pelos dois lados, papéis e busca. */
@Service
public class DirectoryService {

    public record NewInstitution(String legalName, String tradeName, String cnpj, String cnes, Institution.Type type,
            UUID parentId, String city, String uf, Double lat, Double lng) {}

    private final Institutions institutions;
    private final InstitutionMemberships memberships;
    private final InstitutionChecks checks;
    private final CnpjPort cnpj;
    private final CnesPort cnes;
    private final InstitutionTreePolicy tree;
    private final CredentialingService credentialing;

    public DirectoryService(Institutions institutions, InstitutionMemberships memberships, InstitutionChecks checks,
            CnpjPort cnpj, CnesPort cnes, InstitutionTreePolicy tree, CredentialingService credentialing) {
        this.institutions = institutions;
        this.memberships = memberships;
        this.checks = checks;
        this.cnpj = cnpj;
        this.cnes = cnes;
        this.tree = tree;
        this.credentialing = credentialing;
    }

    /** Quem cadastra vira administrador da instituição (vínculo ATIVO); a instituição só aparece depois de conferida. */
    @Transactional
    public Institution register(UUID creatorId, NewInstitution n) {
        List<FieldProblem> problems = new ArrayList<>();
        String cnpjDigits = n.cnpj() == null ? "" : n.cnpj().replaceAll("\\D", "");
        if (!cnpjDigits.matches("\\d{14}")) problems.add(new FieldProblem("cnpj", "Informe o CNPJ com 14 dígitos."));
        if (n.type() == null) problems.add(new FieldProblem("type", "Informe o tipo: NETWORK, HOSPITAL ou UNIT."));
        if (n.type() != Institution.Type.NETWORK && (n.cnes() == null || !n.cnes().replaceAll("\\D", "").matches("\\d{7}"))) {
            problems.add(new FieldProblem("cnes", "Informe o CNES com 7 dígitos (obrigatório para hospital e unidade)."));
        }
        if (isBlank(n.legalName())) problems.add(new FieldProblem("legalName", "Informe a razão social."));
        if (isBlank(n.tradeName())) problems.add(new FieldProblem("tradeName", "Informe o nome usado no dia a dia."));
        if (isBlank(n.city())) problems.add(new FieldProblem("city", "Informe a cidade."));
        if (n.uf() == null || !n.uf().trim().toUpperCase(Locale.ROOT).matches("[A-Z]{2}")) problems.add(new FieldProblem("uf", "Informe a UF com duas letras."));
        if (n.parentId() != null) {
            Optional<Institution> parent = institutions.findById(n.parentId());
            if (parent.isEmpty()) problems.add(new FieldProblem("parentId", "Instituição-mãe não encontrada."));
            else if (!tree.effectiveRole(creatorId, n.parentId()).map(Role::canManageMembers).orElse(false)) {
                problems.add(new FieldProblem("parentId", "Só gestores da instituição-mãe podem criar unidades nela."));
            }
        }
        if (cnpjDigits.matches("\\d{14}") && institutions.findByCnpj(cnpjDigits).isPresent()) {
            problems.add(new FieldProblem("cnpj", "Este CNPJ já está cadastrado. Peça vínculo à instituição existente."));
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        Institution inst = institutions.save(new Institution(n.legalName(), n.tradeName(), cnpjDigits, n.cnes(), n.type(), n.parentId(),
                n.city(), n.uf(), n.lat(), n.lng(), creatorId));
        Role adminRole = n.type() == Institution.Type.NETWORK ? Role.NETWORK_ADMIN : n.type() == Institution.Type.HOSPITAL ? Role.HOSPITAL_ADMIN : Role.UNIT_MANAGER;
        memberships.save(new InstitutionMembership(inst.getId(), creatorId, adminRole, State.ACTIVE));
        verify(inst);
        return inst;
    }

    /** Confere CNPJ (situação ATIVA) e, para hospital/unidade, CNES apontando para o mesmo CNPJ. */
    @Transactional
    public void verify(Institution inst) {
        try {
            Optional<CnpjRecord> r = cnpj.lookup(inst.getCnpj());
            if (r.isEmpty()) {
                reject(inst, cnpj.sourceName(), null, InstitutionCheck.Outcome.NOT_FOUND, "CNPJ não encontrado na Receita Federal.");
                return;
            }
            if (!r.get().isActive()) {
                reject(inst, cnpj.sourceName(), hash(r.get().raw()), InstitutionCheck.Outcome.REJECTED, "Situação cadastral na Receita: " + r.get().situation() + ".");
                return;
            }
            checks.save(new InstitutionCheck(inst.getId(), cnpj.sourceName(), hash(r.get().raw()), InstitutionCheck.Outcome.VERIFIED, "CNPJ ativo: " + r.get().legalName()));
            if (inst.getType() != Institution.Type.NETWORK) {
                Optional<CnesRecord> c = cnes.lookupCnes(inst.getCnes());
                if (c.isEmpty()) {
                    reject(inst, cnes.cnesSourceName(), null, InstitutionCheck.Outcome.NOT_FOUND, "CNES não encontrado no DATASUS.");
                    return;
                }
                if (!c.get().cnpj().replaceAll("\\D", "").equals(inst.getCnpj())) {
                    reject(inst, cnes.cnesSourceName(), hash(c.get().raw()), InstitutionCheck.Outcome.REJECTED, "O CNES informado pertence a outro CNPJ.");
                    return;
                }
                checks.save(new InstitutionCheck(inst.getId(), cnes.cnesSourceName(), hash(c.get().raw()), InstitutionCheck.Outcome.VERIFIED, "CNES confere com o CNPJ."));
            }
            inst.markVerified();
            institutions.save(inst);
        } catch (RegistryUnavailableException e) {
            checks.save(new InstitutionCheck(inst.getId(), "receita/cnes", null, InstitutionCheck.Outcome.UNAVAILABLE, "Fonte indisponível; nova tentativa automática."));
        }
    }

    @Transactional(readOnly = true)
    public List<Institution> search(String q, String uf, Double lat, Double lng, Double radiusKm) {
        return institutions.search(isBlank(q) ? null : q.trim(), isBlank(uf) ? null : uf.trim().toUpperCase(Locale.ROOT), lat, lng, radiusKm);
    }

    @Transactional(readOnly = true)
    public Institution get(UUID id) {
        return institutions.findById(id).filter(i -> i.getState() == Institution.State.VERIFICADA).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public List<Institution> children(UUID id) {
        return institutions.findByParentId(id).stream().filter(i -> i.getState() == Institution.State.VERIFICADA).toList();
    }

    /** Corpo clínico: só vínculos ATIVOS de médicos com selo VERIFIED. */
    @Transactional(readOnly = true)
    public List<InstitutionMembership> clinicalStaff(UUID institutionId) {
        return memberships.findByInstitutionIdAndState(institutionId, State.ACTIVE).stream()
                .filter(m -> credentialing.isVerified(m.getUserId())).toList();
    }

    /** Médico pede vínculo (REQUESTED) ou aceita convite (INVITED → ACTIVE). */
    @Transactional
    public InstitutionMembership requestOrAccept(UUID userId, UUID institutionId) {
        Institution inst = get(institutionId);
        Optional<InstitutionMembership> existing = memberships.findById(new InstitutionMembership.Key(inst.getId(), userId));
        if (existing.isPresent()) {
            InstitutionMembership m = existing.get();
            if (m.getState() == State.INVITED) m.activate();
            return memberships.save(m);
        }
        return memberships.save(new InstitutionMembership(inst.getId(), userId, Role.STAFF_PHYSICIAN, State.REQUESTED));
    }

    /** Gestor convida (INVITED) ou aprova pedido (REQUESTED → ACTIVE). Precisa gerir membros na árvore. */
    @Transactional
    public InstitutionMembership inviteOrApprove(UUID managerId, UUID institutionId, UUID userId) {
        requireManager(managerId, institutionId);
        Optional<InstitutionMembership> existing = memberships.findById(new InstitutionMembership.Key(institutionId, userId));
        if (existing.isPresent()) {
            InstitutionMembership m = existing.get();
            if (m.getState() == State.REQUESTED) m.activate();
            return memberships.save(m);
        }
        return memberships.save(new InstitutionMembership(institutionId, userId, Role.STAFF_PHYSICIAN, State.INVITED));
    }

    /** Atribui papel na árvore do gestor; nunca acima do papel de quem concede. */
    @Transactional
    public InstitutionMembership assignRole(UUID managerId, UUID institutionId, UUID userId, Role role) {
        Role granter = requireManager(managerId, institutionId);
        if (!tree.canGrant(granter, role)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "papel-acima", "Papel acima do seu",
                    "Você pode conceder no máximo o papel " + granter.name() + ".");
        }
        InstitutionMembership m = memberships.findById(new InstitutionMembership.Key(institutionId, userId))
                .filter(x -> x.getState() == State.ACTIVE)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Vínculo não encontrado", "O usuário não tem vínculo ativo com esta instituição."));
        m.setRole(role);
        return memberships.save(m);
    }

    @Transactional
    public void endMembership(UUID actorId, UUID institutionId, UUID userId) {
        if (!actorId.equals(userId)) requireManager(actorId, institutionId);
        memberships.findById(new InstitutionMembership.Key(institutionId, userId)).ifPresent(m -> {
            m.end();
            memberships.save(m);
        });
    }

    public Optional<Role> effectiveRole(UUID userId, UUID institutionId) {
        return tree.effectiveRole(userId, institutionId);
    }

    public List<InstitutionMembership> membershipsAll(UUID institutionId) {
        return memberships.findByInstitutionId(institutionId);
    }

    public List<InstitutionMembership> membershipsOf(UUID userId) {
        return memberships.findByUserIdAndState(userId, State.ACTIVE);
    }

    /** Gestor da árvore: 403 com motivo; instituição fora da árvore: 404 (não revela). */
    private Role requireManager(UUID managerId, UUID institutionId) {
        institutions.findById(institutionId).orElseThrow(this::notFound);
        Role r = tree.effectiveRole(managerId, institutionId).orElseThrow(this::notFound);
        if (!r.canManageMembers()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem permissão de gestão",
                    "Seu papel " + r.name() + " não gerencia membros. Peça a um gestor da instituição.");
        }
        return r;
    }

    private void reject(Institution inst, String source, String hash, InstitutionCheck.Outcome outcome, String reason) {
        checks.save(new InstitutionCheck(inst.getId(), source, hash, outcome, reason));
        inst.markRejected();
        institutions.save(inst);
    }

    private ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A instituição não existe ou você não tem acesso.");
    }

    private static String hash(String raw) {
        return Hashing.sha256Hex(raw == null ? "" : raw);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
