package com.doctorexplorer.core.identity.repo;

import com.doctorexplorer.core.identity.domain.TermsAcceptance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsAcceptances extends JpaRepository<TermsAcceptance, UUID> {}
