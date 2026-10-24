package com.doctorexplorer.core.privacy.repo;

import com.doctorexplorer.core.privacy.domain.DataExportRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataExportRequests extends JpaRepository<DataExportRequest, UUID> {

    void deleteByUserId(UUID userId);
}
