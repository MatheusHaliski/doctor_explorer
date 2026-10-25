package com.doctorexplorer.clinical.ehr;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Encounters extends JpaRepository<Encounter, UUID> {}
