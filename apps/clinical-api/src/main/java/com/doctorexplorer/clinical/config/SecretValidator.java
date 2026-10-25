package com.doctorexplorer.clinical.config;

import org.springframework.context.annotation.Configuration;

/** Recusa subir sem {@code DE_CLINICAL_SECRET} (dev e it trazem um valor só de teste no próprio perfil). */
@Configuration
public class SecretValidator {

    public SecretValidator(ClinicalProperties props) {
        if (!props.hasSecret()) throw new IllegalStateException("DE_CLINICAL_SECRET é obrigatório (Base64 de 32+ bytes).");
        props.secretBytes();
    }
}
