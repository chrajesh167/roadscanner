package com.roadscanner.providerintegrationservice.adapter.in.rest.order;

import com.roadscanner.providerintegrationservice.domain.port.in.CancelBooking;
import com.roadscanner.providerintegrationservice.domain.port.in.GetOrderDetails;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal-only — reading and cancelling a confirmed provider order.
 *
 * <p>The order token is a query parameter on the read and a body field on the cancel, mirroring
 * where each naturally belongs: a read is idempotent and fully described by its URL, a cancel
 * carries intent (the reason) that belongs in a body.
 */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions/{sessionId}/orders/{providerOrderReference}")
@Validated
@Tag(name = "Provider Orders", description = "Read and cancel confirmed provider orders")
class ProviderOrderController {

    private final GetOrderDetails getOrderDetails;
    private final CancelBooking cancelBooking;

    ProviderOrderController(GetOrderDetails getOrderDetails, CancelBooking cancelBooking) {
        this.getOrderDetails = getOrderDetails;
        this.cancelBooking = cancelBooking;
    }

    @GetMapping
    @Operation(summary = "Get order details",
            description = "The provider's own view of a confirmed order, for display and support lookups.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The provider's order payload"),
            @ApiResponse(responseCode = "404", description = "Unknown provider, or one that cannot read orders"),
            @ApiResponse(responseCode = "503", description = "The provider could not be reached")
    })
    ProviderOrderResponse get(@PathVariable String providerType, @PathVariable UUID sessionId,
                             @PathVariable String providerOrderReference,
                             @Parameter(description = "The order token captured at booking")
                             @RequestParam @NotBlank String token) {
        GetOrderDetails.Result result = getOrderDetails.get(new GetOrderDetails.Command(
                new ProviderSessionId(sessionId), providerOrderReference, token));
        return ProviderOrderResponse.from(result.order());
    }

    @PutMapping("/cancel")
    @Operation(summary = "Cancel an order",
            description = "Cancels the entire order and reports the amount the provider actually refunded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled; carries the refunded amount"),
            @ApiResponse(responseCode = "404", description = "Unknown provider, or one that cannot cancel"),
            @ApiResponse(responseCode = "503", description = "The provider could not be reached")
    })
    CancellationResponse cancel(@PathVariable String providerType, @PathVariable UUID sessionId,
                                @PathVariable String providerOrderReference,
                                @Valid @RequestBody CancelOrderRequest request) {
        CancelBooking.Result result = cancelBooking.cancel(new CancelBooking.Command(
                new ProviderSessionId(sessionId), providerOrderReference, request.reason()));
        return CancellationResponse.from(result.cancellation());
    }
}
