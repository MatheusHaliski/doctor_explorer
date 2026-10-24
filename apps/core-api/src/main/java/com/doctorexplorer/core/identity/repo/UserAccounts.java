package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccounts extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailNormalized(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);
}
