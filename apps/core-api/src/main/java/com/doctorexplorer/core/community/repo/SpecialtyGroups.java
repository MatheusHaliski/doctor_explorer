package com.doctorexplorer.core.community.repo;

import com.doctorexplorer.core.community.domain.SpecialtyGroup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyGroups extends JpaRepository<SpecialtyGroup, UUID> {

    Optional<SpecialtyGroup> findBySpecialtyCode(String specialtyCode);
}
