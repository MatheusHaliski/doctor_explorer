package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.UserPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferences extends JpaRepository<UserPreference, UUID> {}
