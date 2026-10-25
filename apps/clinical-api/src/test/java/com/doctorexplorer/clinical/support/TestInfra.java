package com.doctorexplorer.clinical.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL real (banco clínico próprio) e a core-api de mentira para chave pública e auditoria. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfra {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withDatabaseName("doctor_explorer_clinical").withUsername("clinical")
                .withPassword("clinical");
    }

    @Bean
    DynamicPropertyRegistrar coreStub() {
        return registry -> registry.add("doctor-explorer.core.base-url", StubCore::baseUrl);
    }
}
