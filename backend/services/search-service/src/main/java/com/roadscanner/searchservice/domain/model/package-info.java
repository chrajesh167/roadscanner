/**
 * Domain model: the read-model projection
 * ({@link com.roadscanner.searchservice.domain.model.SearchableTrip}) and its value objects.
 * Framework-free by construction — no persistence, web, Kafka, or Spring annotations belong
 * here, and this package compiles without Spring Boot on the classpath, matching
 * {@code auth-service}'s domain-layer discipline.
 *
 * See docs/services/search-service/domain-model.md for the conceptual model this implements —
 * including why this package deliberately has no aggregate with protected business invariants
 * the way {@code auth-service}'s {@code domain.model} does; a read model has projections, not
 * an authoritative lifecycle.
 */
package com.roadscanner.searchservice.domain.model;
