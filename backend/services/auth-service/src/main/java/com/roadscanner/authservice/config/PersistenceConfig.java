package com.roadscanner.authservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit repository/entity scanning for {@code adapter.out.persistence}. Spring Boot's default
 * component scanning (rooted at {@link com.roadscanner.authservice.AuthServiceApplication})
 * would already find these, since the package is a sub-package of the application root — this
 * is here anyway so the wiring is a visible, intentional statement rather than a side effect of
 * where a package happens to sit in the tree, and so it keeps working if the application root
 * or this package ever moves.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.roadscanner.authservice.adapter.out.persistence")
@EntityScan(basePackages = "com.roadscanner.authservice.adapter.out.persistence")
public class PersistenceConfig {
}
