package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A first-party operator, referenced not owned — {@code operatorId} + a denormalized display
 * name, kept current via {@code OperatorUpdated} (docs/services/inventory-service/domain-model.md).
 * {@code operator-service} remains the source of truth; this is a lookup copy joined onto
 * {@link Trip} for first-party trips only — a provider-synced trip has no {@code OperatorRef}. */
public final class OperatorRef {

    private final UUID operatorId;
    private String displayName;

    private OperatorRef(UUID operatorId, String displayName) {
        this.operatorId = Objects.requireNonNull(operatorId, "operatorId must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = displayName;
    }

    public static OperatorRef of(UUID operatorId, String displayName) {
        return new OperatorRef(operatorId, displayName);
    }

    public UUID operatorId() {
        return operatorId;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperatorRef other)) return false;
        return operatorId.equals(other.operatorId);
    }

    @Override
    public int hashCode() {
        return operatorId.hashCode();
    }
}
