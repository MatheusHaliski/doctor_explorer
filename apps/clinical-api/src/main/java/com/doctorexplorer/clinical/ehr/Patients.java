package com.doctorexplorer.clinical.ehr;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Patients extends JpaRepository<Patient, UUID> {

    List<Patient> findTop20ByVaultIdOrderByLastRecordAtDesc(UUID vaultId);

    /** Pacientes do cofre que têm todos os tokens da consulta (índice cego). */
    @Query(value = """
            select p.* from patient p
            where p.vault_id = :vault and p.id in (
                select t.patient_id from patient_name_index t where t.token in (:tokens)
                group by t.patient_id having count(distinct t.token) = :n)
            order by p.last_record_at desc limit 20
            """, nativeQuery = true)
    List<Patient> searchByTokens(@Param("vault") UUID vault, @Param("tokens") Collection<String> tokens, @Param("n") long n);
}
