package com.roadscanner.inventoryservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Explicit repository/entity scanning for {@code adapter.out.persistence} — matching every
 * other service in this codebase's identical {@code PersistenceConfig}. */
@Configuration
@EnableJpaRepositories(basePackages = "com.roadscanner.inventoryservice.adapter.out.persistence")
@EntityScan(basePackages = "com.roadscanner.inventoryservice.adapter.out.persistence")
public class PersistenceConfig {
}
