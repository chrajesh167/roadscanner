package com.roadscanner.bookingservice.domain.model;

/**
 * The platform's coarse-grained RBAC roles, exactly matching {@code auth-service}'s own
 * {@code Role} enum (docs/services/auth-service/security-design.md's "RBAC Strategy") — the
 * value this service reads from the JWT's {@code role} claim, never assigns or mutates itself.
 */
public enum Role {
    TRAVELER,
    OPERATOR,
    ADMIN,
    SUPPORT
}
