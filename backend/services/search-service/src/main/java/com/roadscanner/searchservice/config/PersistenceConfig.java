package com.roadscanner.searchservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit repository/entity scanning — a visible, intentional statement rather than a side
 * effect of package placement, matching {@code auth-service}'s identical {@code PersistenceConfig}.
 *
 * <p>Each persistence package is listed individually rather than widening to the service root:
 * an entity that is not named here is not persisted, which is the point. The location module
 * keeps its own {@code location.adapter.out.persistence} package so the module stays
 * self-contained, so it has to be declared here explicitly.
 */
@Configuration
@EnableJpaRepositories(basePackages = {
        "com.roadscanner.searchservice.adapter.out.persistence",
        "com.roadscanner.searchservice.location.adapter.out.persistence"
})
@EntityScan(basePackages = {
        "com.roadscanner.searchservice.adapter.out.persistence",
        "com.roadscanner.searchservice.location.adapter.out.persistence"
})
public class PersistenceConfig {
}
