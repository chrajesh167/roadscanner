package com.roadscanner.paymentservice.domain.model;

/**
 * The platform's coarse-grained RBAC roles, exactly matching {@code auth-service}'s own
 * {@code Role} enum (docs/services/auth-service/security-design.md's "RBAC Strategy") — read from
 * the JWT's {@code role} claim, never assigned or mutated here.
 */
public enum Role {
    TRAVELER,
    OPERATOR,
    ADMIN,
    SUPPORT
}
