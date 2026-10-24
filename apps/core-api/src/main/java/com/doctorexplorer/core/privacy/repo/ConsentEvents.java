package com.doctorexplorer.core.privacy.repo;

import com.doctorexplorer.core.privacy.domain.ConsentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentEvents extends JpaRepository<ConsentEvent, UUID> {

    List<ConsentEvent> findByUserIdOrderByOccurredAtDesc(UUID userId);
}
