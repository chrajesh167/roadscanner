package com.roadscanner.inventoryservice.adapter.in.rest.availability;

/** Field-for-field identical to {@code search-service}'s already-shipped
 * {@code InventoryAvailabilityResponse} — the frozen contract this endpoint must serve exactly
 * (docs/services/inventory-service/api-summary.md). Do not rename {@code availableSeats}. */
public record AvailabilityResponse(int availableSeats) {
}
