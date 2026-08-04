package com.roadscanner.providerintegrationservice.domain.model;

/**
 * What a provider can actually do, as advertised by its adapter.
 *
 * <p>An adapter declares only the capabilities its provider's documented API supports. Declaring a
 * capability that then fails at call time is worse than not declaring it: callers route work to a
 * provider on the strength of this set, so an aspirational entry turns a clean "not supported"
 * into a failed booking.
 */
public enum ProviderCapability {
    SEARCH,
    SEAT_MAP,
    SEAT_BLOCK,
    SEAT_RELEASE,
    BOOKING_CONFIRMATION,
    BOOKING_CANCELLATION,
    ORDER_DETAILS,
    TICKET_DOWNLOAD,
    HEALTH_CHECK
}
