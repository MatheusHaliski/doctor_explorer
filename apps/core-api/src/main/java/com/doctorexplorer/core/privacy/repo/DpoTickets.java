package com.doctorexplorer.core.privacy.repo;

import com.doctorexplorer.core.privacy.domain.DpoTicket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DpoTickets extends JpaRepository<DpoTicket, UUID> {

    List<DpoTicket> findByStateOrderByDueAtAsc(DpoTicket.State state);

    List<DpoTicket> findByUserIdOrderByOpenedAtDesc(UUID userId);
}
