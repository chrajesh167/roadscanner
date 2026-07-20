package com.roadscanner.providerintegrationservice.domain.model;

/**
 * The platform-defined vocabulary of operations a provider adapter can support — closed by
 * design, unlike {@link ProviderType}: onboarding a new provider never adds a new kind of
 * capability, it only declares a subset of this fixed set (see {@code provider_configurations}
 * and {@code GetProviderCapabilities}).
 */
public enum ProviderCapability {
    SEARCH,
    SEAT_MAP,
    SEAT_BLOCK,
    SEAT_RELEASE,
    BOOKING_CONFIRMATION,
    TICKET_DOWNLOAD,
    HEALTH_CHECK
}
