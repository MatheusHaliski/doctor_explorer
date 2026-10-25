package com.doctorexplorer.clinical.ehr;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Practitioners extends JpaRepository<Practitioner, UUID> {

    /** Upsert idempotente: as primeiras chamadas de um médico chegam em paralelo (lista + cadastro). */
    @Modifying
    @Query(value = """
            insert into practitioner (id, name, crm_number, crm_uf, updated_at) values (:id, :name, :crm, :uf, now())
            on conflict (id) do update set name = excluded.name, crm_number = excluded.crm_number, crm_uf = excluded.crm_uf, updated_at = now()
            where (practitioner.name, practitioner.crm_number, practitioner.crm_uf) is distinct from (excluded.name, excluded.crm_number, excluded.crm_uf)
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("name") String name, @Param("crm") String crm, @Param("uf") String uf);
}
