package com.roadscanner.inventoryservice.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link CatalogTripEventMessage} stays field-for-field identical to search-service's
 * shipped {@code TripEventMessage} (docs/services/inventory-service/events-published.md's claim,
 * repeated in this record's own Javadoc). search-service is a separate Maven module not on this
 * one's classpath, so the comparison is against a hardcoded snapshot of that record's component
 * names/order rather than a direct class reference — if this test ever needs updating, the
 * events-published.md claim needs updating in the same commit.
 */
class CatalogTripEventMessageShapeTest {

    /** search-service's {@code TripEventMessage} record component order, copied verbatim from
     * {@code backend/services/search-service/.../adapter/in/event/TripEventMessage.java}. */
    private static final List<String> SEARCH_SERVICE_TRIP_EVENT_MESSAGE_FIELDS = List.of(
            "eventType", "tripId", "operatorId", "operatorName", "origin", "destination",
            "departureTime", "arrivalTime", "busTypeCategory", "amenities", "fareAmount",
            "fareCurrency", "occurredAt");

    @Test
    void recordComponentNamesAndOrderMatchSearchServicesTripEventMessageExactly() {
        List<String> actual = List.of(CatalogTripEventMessage.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();

        assertThat(actual).containsExactlyElementsOf(SEARCH_SERVICE_TRIP_EVENT_MESSAGE_FIELDS);
    }

    @Test
    void serializesToJsonWithExactlyTheExpectedFieldNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CatalogTripEventMessage message = new CatalogTripEventMessage(CatalogTripEventType.PUBLISHED,
                UUID.randomUUID(), UUID.randomUUID(), "Acme Travels", "Mumbai", "Pune",
                Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"), "AC Sleeper",
                List.of("WiFi"), BigDecimal.valueOf(500), "INR", Instant.parse("2026-07-01T00:00:00Z"));

        String json = mapper.writeValueAsString(message);
        Map<String, Object> asMap = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });

        assertThat(asMap.keySet()).containsExactlyInAnyOrderElementsOf(SEARCH_SERVICE_TRIP_EVENT_MESSAGE_FIELDS);
    }
}
