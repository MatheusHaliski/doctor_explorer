package com.doctorexplorer.clinical.documents;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentDeliveries extends JpaRepository<DocumentDelivery, UUID> {

    /** Trava para o uso único: dois cliques no mesmo link não baixam duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentDelivery d where d.tokenHash = :hash")
    Optional<DocumentDelivery> lockByTokenHash(String hash);

    List<DocumentDelivery> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
