package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.community.domain.GroupMembership;
import com.doctorexplorer.core.community.repo.GroupMemberships;
import com.doctorexplorer.core.community.repo.SpecialtyGroups;
import com.doctorexplorer.core.community.service.CommunityService;
import com.doctorexplorer.core.credentialing.domain.CredentialCheck;
import com.doctorexplorer.core.credentialing.domain.ProfessionalProfile;
import com.doctorexplorer.core.credentialing.repo.CredentialChecks;
import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.SpecialtyClaims;
import com.doctorexplorer.core.identity.domain.SealStatus;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.service.Mailer;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF02 (US06) + RF04 · fila do moderador: selos em revisão manual (fonte do CFM sem resposta por 24 h ou
 * CRM já verificado em outra conta) e pedidos de entrada em grupo sem RQE. Toda decisão fica registrada
 * como {@link CredentialCheck} com a fonte "moderador" e o motivo em pt-BR; o médico é avisado por e-mail
 * sem dado sensível.
 */
@Service
public class ModerationService {

    public static final String SOURCE = "moderador";

    private final ProfessionalProfiles profiles;
    private final SpecialtyClaims claims;
    private final CredentialChecks checks;
    private final UserAccounts accounts;
    private final GroupMemberships groupMemberships;
    private final SpecialtyGroups groups;
    private final CommunityService community;
    private final Mailer mailer;

    public ModerationService(ProfessionalProfiles profiles, SpecialtyClaims claims, CredentialChecks checks, UserAccounts accounts,
            GroupMemberships groupMemberships, SpecialtyGroups groups, CommunityService community, Mailer mailer) {
        this.profiles = profiles;
        this.claims = claims;
        this.checks = checks;
        this.accounts = accounts;
        this.groupMemberships = groupMemberships;
        this.groups = groups;
        this.community = community;
        this.mailer = mailer;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> queue() {
        List<Map<String, Object>> seals = profiles.findBySeal(SealStatus.MANUAL_REVIEW).stream()
                .sorted((a, b) -> a.getSealChangedAt().compareTo(b.getSealChangedAt()))
                .map(this::sealView).toList();
        List<Map<String, Object>> requests = groupMemberships.findByState(GroupMembership.State.REQUESTED).stream()
                .sorted((a, b) -> a.getJoinedAt().compareTo(b.getJoinedAt()))
                .map(m -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("userId", m.getUserId());
                    v.put("userName", nameOf(m.getUserId()));
                    v.put("specialtyCode", groups.findById(m.getGroupId()).map(g -> g.getSpecialtyCode()).orElse("?"));
                    v.put("requestedAt", m.getJoinedAt().toString());
                    return v;
                }).toList();
        return Map.of("seals", seals, "groupRequests", requests);
    }

    /** Aprova ou recusa um selo em revisão manual. Recusa exige motivo; aprovação aceita motivo opcional. */
    @Transactional
    public Map<String, Object> decideSeal(UUID moderatorId, UUID userId, boolean approve, String reason) {
        ProfessionalProfile p = profiles.findById(userId).filter(x -> x.getSeal() == SealStatus.MANUAL_REVIEW).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Nada a revisar", "Esse selo não está em revisão manual."));
        String text = reason == null ? "" : reason.trim();
        if (!approve && text.length() < 10) {
            throw new ValidationProblemException(List.of(new FieldProblem("reason", "Explique a recusa em pelo menos 10 caracteres; o médico vai ler esse motivo.")));
        }
        if (text.length() > 500) throw new ValidationProblemException(List.of(new FieldProblem("reason", "O motivo tem no máximo 500 caracteres.")));
        UserAccount account = accounts.findById(userId).orElseThrow();
        if (approve) {
            p.markVerified();
            checks.save(new CredentialCheck(userId, SOURCE, null, CredentialCheck.Outcome.VERIFIED,
                    "Aprovado em revisão manual" + (text.isEmpty() ? "." : ": " + text)));
            mailer.send(account.getEmail(), "Seu CRM foi verificado", "Olá, " + account.getDisplayName()
                    + ". Um moderador conferiu seus dados e seu selo \"CRM verificado\" está ativo. Agora você pode publicar, conversar e aparecer no diretório.");
        } else {
            p.markRejected();
            checks.save(new CredentialCheck(userId, SOURCE, null, CredentialCheck.Outcome.REJECTED, "Recusado em revisão manual: " + text));
            mailer.send(account.getEmail(), "Não foi possível verificar seu CRM", "Olá, " + account.getDisplayName()
                    + ". Um moderador revisou seus dados e não conseguiu confirmar o CRM. Motivo: " + text + " Corrija os dados no seu perfil e envie de novo.");
        }
        profiles.save(p);
        Map<String, Object> v = new LinkedHashMap<>(sealView(p));
        v.put("decidedBy", moderatorId);
        return v;
    }

    @Transactional
    public GroupMembership approveGroupRequest(String specialtyCode, UUID userId) {
        return community.approve(specialtyCode, userId);
    }

    private Map<String, Object> sealView(ProfessionalProfile p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("userId", p.getUserId());
        v.put("userName", nameOf(p.getUserId()));
        v.put("crmNumber", p.getCrmNumber() == null ? "" : p.getCrmNumber());
        v.put("crmUf", p.getCrmUf() == null ? "" : p.getCrmUf());
        v.put("seal", p.getSeal().name());
        v.put("since", p.getSealChangedAt().toString());
        v.put("specialties", claims.findByUserId(p.getUserId()).stream().map(c -> Map.of("specialtyCode", c.getSpecialtyCode(), "rqe", c.getRqe(), "confirmed", c.isConfirmed())).toList());
        v.put("checks", checks.findTop10ByUserIdOrderByCheckedAtDesc(p.getUserId()).stream().map(c -> Map.of(
                "source", c.getSource(), "checkedAt", c.getCheckedAt().toString(), "outcome", c.getOutcome().name(), "reason", c.getReasonPtBr() == null ? "" : c.getReasonPtBr())).toList());
        return v;
    }

    private String nameOf(UUID userId) {
        return accounts.findById(userId).map(UserAccount::getDisplayName).orElse("Conta excluída");
    }
}
