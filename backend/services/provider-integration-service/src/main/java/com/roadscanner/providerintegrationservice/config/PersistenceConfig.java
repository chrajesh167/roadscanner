package com.roadscanner.providerintegrationservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Explicit repository/entity scanning for {@code adapter.out.persistence} — a visible,
 * intentional statement rather than a side effect of package placement, matching
 * {@code auth-service}/{@code search-service}'s identical {@code PersistenceConfig}. */
@Configuration
@EnableJpaRepositories(basePackages = "com.roadscanner.providerintegrationservice.adapter.out.persistence")
@EntityScan(basePackages = "com.roadscanner.providerintegrationservice.adapter.out.persistence")
public class PersistenceConfig {
}
