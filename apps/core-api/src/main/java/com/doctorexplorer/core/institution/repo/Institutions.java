package com.doctorexplorer.core.institution.repo;

import com.doctorexplorer.core.institution.domain.Institution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Institutions extends JpaRepository<Institution, UUID> {

    Optional<Institution> findByCnpj(String cnpj);

    List<Institution> findByParentId(UUID parentId);

    /**
     * Busca por texto, UF e raio (Haversine em SQL, km). PostGIS geography + GiST substitui quando o
     * diretório crescer (ADR-013). Só instituições VERIFICADA; distância nula quando não há coordenadas.
     */
    @Query(value = """
            select i.*,
                   case when i.lat is null or :lat is null then null
                        else 6371 * acos(least(1.0, cos(radians(cast(:lat as double precision))) * cos(radians(i.lat))
                             * cos(radians(i.lng) - radians(cast(:lng as double precision)))
                             + sin(radians(cast(:lat as double precision))) * sin(radians(i.lat)))) end as distance_km
            from institution i
            where i.state = 'VERIFICADA'
              and (:q is null or lower(i.trade_name) like lower(concat('%', cast(:q as text), '%')) or lower(i.city) like lower(concat('%', cast(:q as text), '%')))
              and (:uf is null or i.uf = cast(:uf as text))
              and (:lat is null or :radiusKm is null or i.lat is null or
                   6371 * acos(least(1.0, cos(radians(cast(:lat as double precision))) * cos(radians(i.lat))
                             * cos(radians(i.lng) - radians(cast(:lng as double precision)))
                             + sin(radians(cast(:lat as double precision))) * sin(radians(i.lat)))) <= cast(:radiusKm as double precision))
            order by distance_km nulls last, i.trade_name
            limit 50
            """, nativeQuery = true)
    List<Institution> search(@Param("q") String q, @Param("uf") String uf, @Param("lat") Double lat, @Param("lng") Double lng,
            @Param("radiusKm") Double radiusKm);
}
