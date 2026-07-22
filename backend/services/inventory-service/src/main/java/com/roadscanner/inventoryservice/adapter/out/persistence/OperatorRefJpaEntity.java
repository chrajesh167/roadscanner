package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "operator_refs")
public class OperatorRefJpaEntity {

    @Id
    @Column(name = "operator_id", nullable = false, updatable = false)
    private UUID operatorId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected OperatorRefJpaEntity() {
    }

    OperatorRefJpaEntity(UUID operatorId, String displayName) {
        this.operatorId = operatorId;
        this.displayName = displayName;
    }

    void applyMutableState(String displayName) {
        this.displayName = displayName;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
