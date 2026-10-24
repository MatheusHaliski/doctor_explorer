package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.Credential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Credentials extends JpaRepository<Credential, UUID> {}
