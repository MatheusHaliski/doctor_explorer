package com.doctorexplorer.clinical.documents;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTemplates extends JpaRepository<DocumentTemplate, UUID> {

    List<DocumentTemplate> findByAuthorIdAndKindOrderByNameAsc(UUID authorId, ClinicalDocument.Kind kind);
}
