package com.doctorexplorer.core.credentialing.service;

import com.doctorexplorer.core.common.Hashing;
import com.doctorexplorer.core.credentialing.domain.CredentialCheck;
import com.doctorexplorer.core.credentialing.domain.ProfessionalProfile;
import com.doctorexplorer.core.credentialing.domain.SpecialtyClaim;
import com.doctorexplorer.core.credentialing.repo.CredentialChecks;
import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.SpecialtyClaims;
import com.doctorexplorer.core.credentialing.service.CfmRegistryPort.CfmRecord;
import com.doctorexplorer.core.credentialing.service.CfmRegistryPort.RegistryUnavailableException;
import com.doctorexplorer.core.identity.domain.SealStatus;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF02 · Verificação de CRM/RQE. Compara nome, situação e RQEs com a fonte do CFM, guarda a evidência e
 * move o selo. Fonte fora do ar: fica PENDING e, depois de 24 h, vai para revisão manual.
 */
@Service
public class CredentialingService {

    public record ClaimInput(String specialtyCode, String rqe) {}

    public record Status(SealStatus seal, String crmNumber, String crmUf, List<SpecialtyClaim> specialties, List<CredentialCheck> checks) {}

    static final Duration MANUAL_REVIEW_AFTER = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(CredentialingService.class);

    /** Especialidades aceitas no MVP e o nome como a fonte do CFM escreve. */
    static final Map<String, String> SPECIALTIES = Map.of(
            "CARDIO", "CARDIOLOGIA", "UTI", "MEDICINA INTENSIVA", "DERM", "DERMATOLOGIA", "PED", "PEDIATRIA",
            "ORTO", "ORTOPEDIA E TRAUMATOLOGIA", "NEURO", "NEUROLOGIA", "CLIN", "CLINICA MEDICA");

    private final ProfessionalProfiles profiles;
    private final SpecialtyClaims claims;
    private final CredentialChecks checks;
    private final UserAccounts accounts;
    private final CfmRegistryPort registry;

    public CredentialingService(ProfessionalProfiles profiles, SpecialtyClaims claims, CredentialChecks checks,
            UserAccounts accounts, CfmRegistryPort registry) {
        this.profiles = profiles;
        this.claims = claims;
        this.checks = checks;
        this.accounts = accounts;
        this.registry = registry;
    }

    @Transactional
    public Status submit(UUID userId, String crmNumber, String crmUf, List<ClaimInput> inputs) {
        List<FieldProblem> problems = new ArrayList<>();
        if (crmNumber == null || !crmNumber.replaceAll("\\D", "").matches("\\d{4,7}")) {
            problems.add(new FieldProblem("crmNumber", "Informe o número do CRM (4 a 7 dígitos)."));
        }
        if (crmUf == null || !crmUf.trim().toUpperCase(Locale.ROOT).matches("[A-Z]{2}")) {
            problems.add(new FieldProblem("crmUf", "Informe a UF do CRM com duas letras."));
        }
        for (int i = 0; i < (inputs == null ? 0 : inputs.size()); i++) {
            ClaimInput c = inputs.get(i);
            if (c.specialtyCode() == null || !SPECIALTIES.containsKey(c.specialtyCode().trim().toUpperCase(Locale.ROOT))) {
                problems.add(new FieldProblem("specialties[" + i + "].specialtyCode", "Especialidade desconhecida. Use um dos códigos: " + String.join(", ", SPECIALTIES.keySet().stream().sorted().toList()) + "."));
            }
            if (c.rqe() == null || !c.rqe().replaceAll("\\D", "").matches("\\d{3,7}")) {
                problems.add(new FieldProblem("specialties[" + i + "].rqe", "Informe o RQE (3 a 7 dígitos)."));
            }
        }
        if (problems.isEmpty() && heldByAnotherVerifiedAccount(userId, crmNumber.replaceAll("\\D", ""), crmUf.trim().toUpperCase(Locale.ROOT))) {
            problems.add(new FieldProblem("crmNumber", "Este CRM já está verificado em outra conta. Se for seu, fale com o Encarregado pelo canal de privacidade."));
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        ProfessionalProfile profile = profiles.findById(userId).orElseGet(() -> new ProfessionalProfile(userId));
        profile.submitCrm(crmNumber, crmUf);
        profiles.save(profile);
        claims.deleteByUserId(userId);
        if (inputs != null) {
            for (ClaimInput c : inputs) claims.save(new SpecialtyClaim(userId, c.specialtyCode(), c.rqe()));
        }
        verify(profile);
        return status(userId);
    }

    /** Consulta a fonte e move o selo. Chamado no envio e pelo job diário de reverificação. */
    @Transactional
    public void verify(ProfessionalProfile profile) {
        UUID userId = profile.getUserId();
        UserAccount account = accounts.findById(userId).orElseThrow();
        Optional<CfmRecord> record;
        try {
            record = registry.lookup(profile.getCrmNumber(), profile.getCrmUf());
        } catch (RegistryUnavailableException e) {
            log.warn("Fonte do CFM indisponível para o usuário {}: {}", userId, e.getMessage());
            checks.save(new CredentialCheck(userId, registry.sourceName(), null, CredentialCheck.Outcome.UNAVAILABLE,
                    "Fonte do CFM indisponível; nova tentativa automática."));
            if (profile.getSeal() == SealStatus.PENDING && profile.getSealChangedAt().plus(MANUAL_REVIEW_AFTER).isBefore(Instant.now())) {
                profile.markManualReview();
                profiles.save(profile);
            }
            return;
        }
        if (record.isEmpty()) {
            reject(profile, null, CredentialCheck.Outcome.NOT_FOUND, "CRM " + profile.getCrmNumber() + "/" + profile.getCrmUf() + " não encontrado na fonte do CFM. Confira o número e a UF.");
            return;
        }
        CfmRecord r = record.get();
        String hash = Hashing.sha256Hex(r.rawResponse() == null ? "" : r.rawResponse());
        if (!r.isRegular()) {
            reject(profile, hash, CredentialCheck.Outcome.REJECTED, "Situação no CFM: " + r.situation() + ". Só inscrição regular pode ser verificada.");
            return;
        }
        if (!sameName(account.getDisplayName(), r.name())) {
            reject(profile, hash, CredentialCheck.Outcome.REJECTED, "O nome da conta não confere com o nome registrado no CRM. Use o nome como consta no CFM.");
            return;
        }
        List<SpecialtyClaim> declared = claims.findByUserId(userId);
        int unconfirmed = 0;
        for (SpecialtyClaim c : declared) {
            String expectedName = SPECIALTIES.get(c.getSpecialtyCode());
            boolean ok = r.specialties().stream().anyMatch(s -> fold(s.name()).equals(fold(expectedName)) && s.rqe().replaceAll("\\D", "").equals(c.getRqe()));
            c.setConfirmed(ok);
            claims.save(c);
            if (!ok) unconfirmed++;
        }
        if (profile.getSeal() != SealStatus.VERIFIED && heldByAnotherVerifiedAccount(userId, profile.getCrmNumber(), profile.getCrmUf())) {
            // possível fraude ou conta duplicada: ninguém fica verificado sem um moderador olhar
            checks.save(new CredentialCheck(userId, registry.sourceName(), hash, CredentialCheck.Outcome.REJECTED,
                    "CRM já verificado em outra conta; encaminhado para revisão manual."));
            profile.markManualReview();
            profiles.save(profile);
            return;
        }
        String reason = unconfirmed == 0 ? "CRM e RQE conferidos na fonte do CFM."
                : "CRM conferido; " + unconfirmed + " especialidade(s) sem RQE correspondente ficaram sem confirmação.";
        checks.save(new CredentialCheck(userId, registry.sourceName(), hash, CredentialCheck.Outcome.VERIFIED, reason));
        if (profile.getSeal() != SealStatus.VERIFIED) profile.markVerified();
        profiles.save(profile);
    }

    /** Job diário: a situação no CFM pode mudar (suspensão, cassação); selo perde validade na hora. */
    @Transactional
    public int reverifyAll() {
        int revoked = 0;
        for (ProfessionalProfile p : profiles.findBySeal(SealStatus.VERIFIED)) {
            try {
                Optional<CfmRecord> r = registry.lookup(p.getCrmNumber(), p.getCrmUf());
                if (r.isEmpty() || !r.get().isRegular()) {
                    checks.save(new CredentialCheck(p.getUserId(), registry.sourceName(), null, CredentialCheck.Outcome.REJECTED,
                            r.isEmpty() ? "CRM não encontrado na reverificação." : "Situação no CFM: " + r.get().situation() + "."));
                    p.markRevoked();
                    profiles.save(p);
                    revoked++;
                }
            } catch (RegistryUnavailableException e) {
                log.warn("Reverificação adiada para {}: fonte indisponível", p.getUserId());
            }
        }
        return revoked;
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        ProfessionalProfile p = profiles.findById(userId).orElseGet(() -> new ProfessionalProfile(userId));
        return new Status(p.getSeal(), p.getCrmNumber(), p.getCrmUf(), claims.findByUserId(userId), checks.findTop10ByUserIdOrderByCheckedAtDesc(userId));
    }

    public boolean isVerified(UUID userId) {
        return profiles.findById(userId).map(p -> p.getSeal() == SealStatus.VERIFIED).orElse(false);
    }

    private boolean heldByAnotherVerifiedAccount(UUID userId, String crmNumber, String crmUf) {
        return profiles.findByCrmUfAndCrmNumberAndSeal(crmUf, crmNumber, SealStatus.VERIFIED)
                .map(other -> !other.getUserId().equals(userId)).orElse(false);
    }

    private void reject(ProfessionalProfile profile, String hash, CredentialCheck.Outcome outcome, String reason) {
        checks.save(new CredentialCheck(profile.getUserId(), registry.sourceName(), hash, outcome, reason));
        profile.markRejected();
        profiles.save(profile);
    }

    /** "Dra. Ana Ribeiro" ≈ "ANA RIBEIRO": ignora títulos, acentos, caixa e espaços repetidos. */
    static boolean sameName(String account, String registry) {
        return fold(account).equals(fold(registry));
    }

    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replaceAll("^(dr|dra|prof|profa)\\.?\\s+", "");
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
