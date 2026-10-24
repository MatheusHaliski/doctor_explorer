package com.doctorexplorer.core.credentialing.repo;

import com.doctorexplorer.core.credentialing.domain.CredentialCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialChecks extends JpaRepository<CredentialCheck, UUID> {

    List<CredentialCheck> findTop10ByUserIdOrderByCheckedAtDesc(UUID userId);
}
