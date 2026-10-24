package com.doctorexplorer.core.credentialing.repo;

import com.doctorexplorer.core.credentialing.domain.SpecialtyClaim;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyClaims extends JpaRepository<SpecialtyClaim, UUID> {

    List<SpecialtyClaim> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
