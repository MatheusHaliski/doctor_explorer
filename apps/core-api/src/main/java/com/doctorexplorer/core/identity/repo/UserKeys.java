package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.UserKey;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKeys extends JpaRepository<UserKey, UUID> {}
