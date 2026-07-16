package com.roadscanner.authservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The persistence-shape counterpart of {@link com.roadscanner.authservice.domain.model.RoleAssignment}.
 * Append-only: rows are immutable facts, never updated after insert (see V2 migration), which
 * is why — unlike the other entities in this package — there is no {@code @Version} column and
 * no {@code applyMutableState}: there is no mutable state for optimistic locking to protect.
 */
@Entity
@Table(name = "role_assignments")
public class RoleAssignmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, updatable = false)
    private String role;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    /** Required by JPA/Hibernate for entity instantiation via reflection. */
    protected RoleAssignmentJpaEntity() {
    }

    RoleAssignmentJpaEntity(UUID id, UUID userId, String role, String assignedBy, Instant assignedAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
