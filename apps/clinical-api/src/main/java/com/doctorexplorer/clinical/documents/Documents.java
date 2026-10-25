package com.doctorexplorer.clinical.documents;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface Documents extends JpaRepository<ClinicalDocument, UUID> {

    List<ClinicalDocument> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<ClinicalDocument> findTop50ByVaultIdAndAuthorIdOrderByCreatedAtDesc(UUID vaultId, UUID authorId);

    Optional<ClinicalDocument> findByValidationCodeHash(String hash);

    /** Assinar, enviar e cancelar o mesmo documento ficam em fila. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ClinicalDocument d where d.id = :id")
    Optional<ClinicalDocument> lockById(UUID id);
}
