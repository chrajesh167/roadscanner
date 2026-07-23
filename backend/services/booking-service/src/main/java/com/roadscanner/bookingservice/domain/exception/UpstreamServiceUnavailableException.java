package com.roadscanner.bookingservice.domain.exception;

/** Thrown when {@code inventory-service} or {@code provider-integration-service} is unreachable
 * or returns an error. Unlike {@code inventory-service}'s own "degrade, not fail" posture toward
 * {@code search-service}, this service must fail the operation with a clear, retryable error
 * instead — NFR-7 forbids proceeding with a hold or booking against a trip that can't be
 * verified (docs/services/booking-service/boundaries.md's "Failure mode" sections). */
public class UpstreamServiceUnavailableException extends BookingServiceException {

    public UpstreamServiceUnavailableException(String upstreamServiceName, String detail) {
        super(upstreamServiceName + " is unavailable: " + detail);
    }
}
