package com.doctorexplorer.core.credentialing.repo;

import com.doctorexplorer.core.credentialing.domain.ProfessionalProfile;
import com.doctorexplorer.core.identity.domain.SealStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalProfiles extends JpaRepository<ProfessionalProfile, UUID> {

    List<ProfessionalProfile> findBySeal(SealStatus seal);

    Optional<ProfessionalProfile> findByCrmUfAndCrmNumberAndSeal(String crmUf, String crmNumber, SealStatus seal);
}
