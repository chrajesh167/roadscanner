package com.roadscanner.searchservice;

import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The foundation-level smoke test: proves the full skeleton — configuration, JPA, Flyway,
 * Redis, Kafka listener registration, actuator, and OpenAPI wiring — boots successfully against
 * real Postgres, Redis, and Kafka (Testcontainers). Matching {@code auth-service}'s identical
 * {@code AuthServiceApplicationTests}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SearchServiceApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty — a failing context load is the assertion.
    }
}
