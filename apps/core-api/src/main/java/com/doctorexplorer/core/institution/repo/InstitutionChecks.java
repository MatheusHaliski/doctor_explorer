package com.doctorexplorer.core.institution.repo;

import com.doctorexplorer.core.institution.domain.InstitutionCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionChecks extends JpaRepository<InstitutionCheck, UUID> {

    List<InstitutionCheck> findByInstitutionIdOrderByCheckedAtDesc(UUID institutionId);
}
