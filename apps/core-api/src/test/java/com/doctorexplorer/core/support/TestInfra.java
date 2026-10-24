package com.doctorexplorer.core.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infra real para os {@code *IT}: PostGIS e Valkey em contêineres (Testcontainers + @ServiceConnection).
 * Sem Docker (ex.: máquina de dev restrita), defina {@code DE_IT_EXTERNAL=true} e as variáveis
 * {@code DE_DB_URL}, {@code DE_VALKEY_HOST}, {@code DE_VALKEY_PORT} apontando para serviços já de pé.
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnExpression("#{systemEnvironment['DE_IT_EXTERNAL'] != 'true'}")
public class TestInfra {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("doctor_explorer").withUsername("de").withPassword("de");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> valkey() {
        return new GenericContainer<>(DockerImageName.parse("valkey/valkey:9")).withExposedPorts(6379);
    }
}
