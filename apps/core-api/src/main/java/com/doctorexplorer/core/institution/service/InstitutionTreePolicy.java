package com.doctorexplorer.core.institution.service;

import com.doctorexplorer.core.institution.domain.Institution;
import com.doctorexplorer.core.institution.domain.InstitutionMembership;
import com.doctorexplorer.core.institution.domain.InstitutionMembership.Role;
import com.doctorexplorer.core.institution.repo.InstitutionMemberships;
import com.doctorexplorer.core.institution.repo.Institutions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * RF12 · Papéis institucionais herdam para baixo na árvore rede → hospital → unidade, nunca para cima.
 * O papel efetivo de um usuário numa instituição é o maior papel ATIVO que ele tem nela ou em um ancestral.
 */
@Component
public class InstitutionTreePolicy {

    private final Institutions institutions;
    private final InstitutionMemberships memberships;

    public InstitutionTreePolicy(Institutions institutions, InstitutionMemberships memberships) {
        this.institutions = institutions;
        this.memberships = memberships;
    }

    public boolean isAncestorOrSelf(UUID ancestorId, UUID institutionId) {
        UUID cur = institutionId;
        for (int depth = 0; cur != null && depth < 10; depth++) {
            if (cur.equals(ancestorId)) return true;
            cur = institutions.findById(cur).map(Institution::getParentId).orElse(null);
        }
        return false;
    }

    /** Maior papel ativo do usuário na instituição ou em qualquer ancestral. */
    public Optional<Role> effectiveRole(UUID userId, UUID institutionId) {
        Role best = null;
        UUID cur = institutionId;
        for (int depth = 0; cur != null && depth < 10; depth++) {
            Optional<InstitutionMembership> m = memberships.findById(new InstitutionMembership.Key(cur, userId))
                    .filter(x -> x.getState() == InstitutionMembership.State.ACTIVE);
            if (m.isPresent() && (best == null || m.get().getRole().rank > best.rank)) best = m.get().getRole();
            cur = institutions.findById(cur).map(Institution::getParentId).orElse(null);
        }
        return Optional.ofNullable(best);
    }

    /** A instituição e todas as descendentes (BFS, até 10 níveis): escopo de painéis e auditoria da árvore (RF13). */
    public List<UUID> subtree(UUID rootId) {
        List<UUID> out = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>(List.of(rootId));
        while (!queue.isEmpty() && out.size() < 10_000) {
            UUID cur = queue.poll();
            if (out.contains(cur)) continue;
            out.add(cur);
            institutions.findByParentId(cur).forEach(child -> queue.add(child.getId()));
        }
        return out;
    }

    /** Quem concede precisa gerir membros e só concede papel igual ou abaixo do seu. */
    public boolean canGrant(Role granter, Role target) {
        return granter.canManageMembers() && target.rank <= granter.rank;
    }
}
