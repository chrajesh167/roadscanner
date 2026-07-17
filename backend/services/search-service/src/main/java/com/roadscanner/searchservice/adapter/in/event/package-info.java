/**
 * Kafka consumers — the only layer aware this service is fed by Kafka at all, mirroring
 * {@code adapter.in.rest}'s role for HTTP. {@link com.roadscanner.searchservice.adapter.in.event.TripEventListener}
 * and {@link com.roadscanner.searchservice.adapter.in.event.ReviewEventListener} translate wire
 * messages into inbound-port commands; the message shapes themselves
 * ({@link com.roadscanner.searchservice.adapter.in.event.TripEventMessage},
 * {@link com.roadscanner.searchservice.adapter.in.event.ReviewSubmittedMessage}) never leak
 * past this package. See docs/services/search-service/events-consumed.md for the events this
 * package implements consumption of.
 */
package com.roadscanner.searchservice.adapter.in.event;
