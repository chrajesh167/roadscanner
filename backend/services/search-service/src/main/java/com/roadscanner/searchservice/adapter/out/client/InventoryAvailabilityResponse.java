package com.roadscanner.searchservice.adapter.out.client;

/**
 * The wire shape of {@code inventory-service}'s "Trip Availability Query" response
 * (docs/architecture/api-inventory.md), as far as this client needs it — a seat count only,
 * never seat-level detail (docs/services/search-service/boundaries.md).
 */
record InventoryAvailabilityResponse(int availableSeats) {
}
