package com.doctorexplorer.core.community.repo;

import com.doctorexplorer.core.community.domain.CasePost;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasePosts extends JpaRepository<CasePost, UUID> {

    /**
     * Feed: publicações visíveis, de audiência NETWORK ou de grupos dos quais o leitor é membro, mais
     * recentes primeiro. Os filtros de tipo/especialidade/origem/estado são aplicados no serviço sobre a
     * página (volume pequeno no MVP; vira SQL quando o feed crescer).
     */
    @Query("""
            select p from CasePost p
            where p.state in (com.doctorexplorer.core.community.domain.CasePost$State.PUBLISHED, com.doctorexplorer.core.community.domain.CasePost$State.RESOLVED)
              and (p.audience = com.doctorexplorer.core.community.domain.CasePost$Audience.NETWORK
                   or (p.audience = com.doctorexplorer.core.community.domain.CasePost$Audience.GROUP and p.groupId in :groupIds)
                   or (p.audience = com.doctorexplorer.core.community.domain.CasePost$Audience.INSTITUTION and p.institutionId in :institutionIds))
            order by p.createdAt desc
            """)
    List<CasePost> feed(@Param("groupIds") Collection<UUID> groupIds, @Param("institutionIds") Collection<UUID> institutionIds, Pageable pageable);

    List<CasePost> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
}
