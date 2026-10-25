package com.doctorexplorer.clinical.ehr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientNameTokens extends JpaRepository<PatientNameToken, PatientNameToken.Key> {}
