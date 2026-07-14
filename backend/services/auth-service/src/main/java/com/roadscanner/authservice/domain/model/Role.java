package com.roadscanner.authservice.domain.model;

/**
 * The platform's coarse-grained RBAC roles, per
 * docs/services/auth-service/security-design.md ("RBAC Strategy") and
 * docs/architecture/high-level-design.md §8. Deliberately coarse — fine-grained, resource-level
 * authorization is enforced by the service that owns the resource, never here. See
 * docs/services/auth-service/responsibilities.md for the boundary this enforces.
 *
 * New Phase 2+ actor types (e.g. an Airline Operator, per
 * docs/requirements/actors.md future actors) are expected to add values here rather than
 * requiring a redesign — see docs/services/auth-service/implementation-roadmap.md
 * "Future Extensibility".
 */
public enum Role {
    TRAVELER,
    OPERATOR,
    ADMIN,
    SUPPORT
}
