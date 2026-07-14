package com.roadscanner.authservice;

import com.roadscanner.authservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The foundation-level smoke test: proves the full skeleton — configuration, JPA, Flyway,
 * Redis, actuator, and OpenAPI wiring — boots successfully against real Postgres and Redis
 * (Testcontainers), before any business logic exists. See
 * docs/services/auth-service/testing-strategy.md.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty — a failing context load is the assertion.
    }
}
