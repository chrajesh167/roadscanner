package com.roadscanner.providerintegrationservice.adapter.in.rest.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @param token  the order token captured at booking; providers authorise cancellation with it
 * @param reason free text recorded with the provider, for support and dispute handling
 */
record CancelOrderRequest(
        @NotBlank @Schema(description = "The order token captured at booking") String token,
        @Schema(example = "order cancelled by passenger request") String reason) {
}
