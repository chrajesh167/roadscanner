package com.roadscanner.authservice.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and Redis via Testcontainers, per docs/services/auth-service/testing-strategy.md
 * — integration tests run against the real thing, not an in-memory substitute, so a test proves
 * what production will actually do.
 *
 * Uses Spring Boot's @ServiceConnection (3.1+): container connection details (JDBC URL,
 * credentials, host/port) are wired into the Spring context automatically — no manual
 * @DynamicPropertySource boilerplate needed here or in any test that imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
