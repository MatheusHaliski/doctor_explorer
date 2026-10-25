package com.doctorexplorer.clinical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** clinical-api: prontuário SOAP, cofre clínico e FHIR R4 (RF08). Só a core-api fala com ela, por token interno. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClinicalApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicalApiApplication.class, args);
    }
}
