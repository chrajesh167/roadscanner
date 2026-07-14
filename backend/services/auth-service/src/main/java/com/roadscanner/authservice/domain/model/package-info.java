/**
 * Domain model: entities ({@link com.roadscanner.authservice.domain.model.Credential},
 * {@link com.roadscanner.authservice.domain.model.RefreshToken},
 * {@link com.roadscanner.authservice.domain.model.PasswordResetRequest},
 * {@link com.roadscanner.authservice.domain.model.RoleAssignment}) and their value objects.
 * Framework-free by construction — no persistence, web, or Spring annotations belong here, and
 * this package compiles without Spring Boot on the classpath.
 *
 * See docs/services/auth-service/database-design.md for the conceptual data model this
 * implements, and docs/services/auth-service/package-structure.md for the layering this
 * package sits within.
 */
package com.roadscanner.authservice.domain.model;
