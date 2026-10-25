package com.doctorexplorer.core.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** doctor-explorer.clinical.*: onde fica a clinical-api na rede interna (RF08, ADR-001). */
@ConfigurationProperties("doctor-explorer.clinical")
public record ClinicalProperties(@DefaultValue("http://localhost:8081") String baseUrl, @DefaultValue("15s") Duration timeout) {}
