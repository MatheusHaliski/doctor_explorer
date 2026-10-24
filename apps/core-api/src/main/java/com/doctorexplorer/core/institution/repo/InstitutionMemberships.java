package com.doctorexplorer.core.institution.repo;

import com.doctorexplorer.core.institution.domain.InstitutionMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionMemberships extends JpaRepository<InstitutionMembership, InstitutionMembership.Key> {

    List<InstitutionMembership> findByUserIdAndState(UUID userId, InstitutionMembership.State state);

    List<InstitutionMembership> findByInstitutionIdAndState(UUID institutionId, InstitutionMembership.State state);

    List<InstitutionMembership> findByInstitutionId(UUID institutionId);
}
