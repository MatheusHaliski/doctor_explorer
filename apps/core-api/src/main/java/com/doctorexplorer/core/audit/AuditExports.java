package com.doctorexplorer.core.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditExports extends JpaRepository<AuditExport, UUID> {

    List<AuditExport> findTop20ByRequestedByOrderByCreatedAtDesc(UUID requestedBy);
}
