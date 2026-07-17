/**
 * Inbound use-case ports: client-facing
 * ({@link com.roadscanner.searchservice.domain.port.in.SearchTrips},
 * {@link com.roadscanner.searchservice.domain.port.in.GetTripDetail},
 * {@link com.roadscanner.searchservice.domain.port.in.GetSearchSuggestions}), event-driven
 * index maintenance
 * ({@link com.roadscanner.searchservice.domain.port.in.IndexTripPublished},
 * {@link com.roadscanner.searchservice.domain.port.in.IndexTripUpdated},
 * {@link com.roadscanner.searchservice.domain.port.in.IndexTripCancelled},
 * {@link com.roadscanner.searchservice.domain.port.in.UpdateRatingSnapshot}), and operational
 * ({@link com.roadscanner.searchservice.domain.port.in.RebuildIndex}).
 *
 * Interfaces only — see docs/services/search-service/use-cases.md for the use cases these
 * represent. Implementations live in {@code application.usecase}. Each interface's
 * Command/Result records are built entirely from domain types, never a raw Kafka message or
 * HTTP request/response type — see each interface's own Javadoc.
 */
package com.roadscanner.searchservice.domain.port.in;
