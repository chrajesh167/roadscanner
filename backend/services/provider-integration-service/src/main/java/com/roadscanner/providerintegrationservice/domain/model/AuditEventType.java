package com.roadscanner.providerintegrationservice.domain.model;

/** The three events this service publishes (see docs/services/provider-integration-service/events-published.md). */
public enum AuditEventType {
    PROVIDER_UNAVAILABLE,
    PROVIDER_RECOVERED,
    SESSION_EXPIRED
}
