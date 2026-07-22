package com.roadscanner.inventoryservice.adapter.out.kafka;

import java.time.Instant;
import java.util.UUID;

public record OperatorUpdatedMessage(UUID operatorId, String displayName, Instant occurredAt) {
}
