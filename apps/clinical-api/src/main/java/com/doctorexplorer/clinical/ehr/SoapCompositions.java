package com.doctorexplorer.clinical.ehr;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoapCompositions extends JpaRepository<SoapComposition, UUID> {

    List<SoapComposition> findByPatientIdAndStateNotOrderByCreatedAtDesc(UUID patientId, SoapComposition.State state);

    List<SoapComposition> findByLineageIdOrderByVersionAsc(UUID lineageId);

    boolean existsByLineageIdAndState(UUID lineageId, SoapComposition.State state);
}
