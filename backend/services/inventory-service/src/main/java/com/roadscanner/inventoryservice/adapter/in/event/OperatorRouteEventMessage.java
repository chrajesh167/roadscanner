package com.roadscanner.inventoryservice.adapter.in.event;

import java.util.UUID;

public record OperatorRouteEventMessage(UUID routeId, String origin, String destination) {
}
