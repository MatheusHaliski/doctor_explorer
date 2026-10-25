package com.doctorexplorer.clinical.ehr;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalVaults extends JpaRepository<ClinicalVault, UUID> {

    Optional<ClinicalVault> findByOwnerTypeAndOwnerId(ClinicalVault.OwnerType ownerType, UUID ownerId);

    /** Cria o cofre só se ainda não existe (duas requisições do primeiro uso não criam dois). */
    @Modifying
    @Query(value = """
            insert into clinical_vault (id, owner_type, owner_id, wrapped_dek, key_id, created_at) values (:id, :type, :owner, :dek, :keyId, now())
            on conflict (owner_type, owner_id) do nothing
            """, nativeQuery = true)
    void createIfAbsent(@Param("id") UUID id, @Param("type") String type, @Param("owner") UUID owner, @Param("dek") byte[] dek, @Param("keyId") String keyId);
}
