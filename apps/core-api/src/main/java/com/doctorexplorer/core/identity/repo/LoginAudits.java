package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.LoginAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAudits extends JpaRepository<LoginAudit, UUID> {}
