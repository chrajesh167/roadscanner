package com.roadscanner.providerintegrationservice;

import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The foundation-level smoke test: proves the full skeleton — configuration, JPA, Flyway,
 * Redis, Kafka, Resilience4j, scheduling, actuator, and OpenAPI wiring — boots successfully
 * against real Postgres, Redis, and Kafka (Testcontainers). Matching {@code auth-service}/
 * {@code search-service}'s identical smoke test. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProviderIntegrationServiceApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty — a failing context load is the assertion.
    }
}
