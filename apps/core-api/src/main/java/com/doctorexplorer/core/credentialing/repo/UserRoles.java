package com.doctorexplorer.core.credentialing.repo;

import com.doctorexplorer.core.credentialing.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoles extends JpaRepository<UserRole, UserRole.Key> {

    List<UserRole> findByUserId(UUID userId);
}
