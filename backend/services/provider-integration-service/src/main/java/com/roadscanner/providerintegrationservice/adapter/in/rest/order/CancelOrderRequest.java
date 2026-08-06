package com.roadscanner.providerintegrationservice.adapter.in.rest.order;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The order token is deliberately not a field here.
 *
 * <p>It used to be required from the caller, which made cancellation unreachable: the token is
 * issued once with the order and was never returned to anyone. It is now resolved from the stored
 * {@code ProviderBooking}, keeping a provider credential inside the service that owns provider
 * vocabulary rather than asking every caller to carry one.
 *
 * @param reason free text recorded with the provider, for support and dispute handling
 */
record CancelOrderRequest(
        @Schema(example = "order cancelled by passenger request") String reason) {
}
