package com.doctorexplorer.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Recusa subir sem {@code DE_APP_SECRET} fora dos perfis dev/it. */
@Configuration
@EnableConfigurationProperties({AppSecretProperties.class, com.doctorexplorer.core.audit.AuditProperties.class, com.doctorexplorer.core.storage.StorageProperties.class,
        com.doctorexplorer.core.media.MediaProperties.class, com.doctorexplorer.core.internal.ClinicalProperties.class,
        com.doctorexplorer.core.calls.CallProperties.class})
public class AppSecretValidator {

    public AppSecretValidator(AppSecretProperties props, Environment env) {
        boolean relaxed = env.acceptsProfiles(Profiles.of("dev", "it"));
        if (!props.hasSecret()) {
            if (relaxed) {
                throw new IllegalStateException("Perfil dev/it sem DE_APP_SECRET: defina em application-dev.yml ou no ambiente.");
            }
            throw new IllegalStateException("DE_APP_SECRET é obrigatório fora dos perfis dev/it (Base64 de 32+ bytes).");
        }
        props.secretBytes(); // valida tamanho já na subida
    }
}
