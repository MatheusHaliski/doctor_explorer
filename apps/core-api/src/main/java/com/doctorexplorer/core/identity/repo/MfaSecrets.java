package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.MfaSecret;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaSecrets extends JpaRepository<MfaSecret, UUID> {}
