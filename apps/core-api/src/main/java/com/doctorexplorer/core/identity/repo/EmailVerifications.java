package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.EmailVerification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerifications extends JpaRepository<EmailVerification, UUID> {}
