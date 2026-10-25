package com.doctorexplorer.core.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infra real para os {@code *IT}: PostGIS, Valkey, Cassandra (auditoria, RF13) e Garage (S3 compatível, o mesmo do
 * Compose: exportações e mídia) em contêineres (Testcontainers). Sem Docker, defina {@code DE_IT_EXTERNAL=true} e aponte
 * {@code DE_DB_URL}, {@code DE_VALKEY_HOST/PORT}, {@code DE_CASSANDRA_CONTACT_POINTS} e {@code DE_S3_*} para
 * serviços já de pé.
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

    @Bean
    @ServiceConnection
    CassandraContainer cassandra() {
        // memória enxuta para caber no runner do CI
        return new CassandraContainer(DockerImageName.parse("cassandra:5.0"))
                .withEnv("MAX_HEAP_SIZE", "768M").withEnv("HEAP_NEWSIZE", "128M");
    }

    @Bean
    GarageContainer garage() {
        return new GarageContainer();
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(GarageContainer garage) {
        return registry -> {
            registry.add("doctor-explorer.storage.endpoint", garage::s3Url);
            registry.add("doctor-explorer.storage.access-key", () -> GarageContainer.ACCESS_KEY);
            registry.add("doctor-explorer.storage.secret-key", () -> GarageContainer.SECRET_KEY);
            registry.add("doctor-explorer.storage.region", () -> "garage");
            registry.add("doctor-explorer.storage.create-buckets", () -> "false");
        };
    }
}
