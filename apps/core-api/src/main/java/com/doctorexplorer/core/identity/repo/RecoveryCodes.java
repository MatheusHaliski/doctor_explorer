package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.RecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodes extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
