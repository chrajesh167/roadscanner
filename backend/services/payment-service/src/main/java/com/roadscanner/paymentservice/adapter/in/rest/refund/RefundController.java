package com.roadscanner.paymentservice.adapter.in.rest.refund;

import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.port.in.GetRefund;
import com.roadscanner.paymentservice.domain.port.in.InitiateRefund;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.UUID;

/**
 * The <strong>internal</strong>, service-to-service refund surface — the authoritative refund
 * trigger, consumed by {@code booking-service} and admin-console (support overrides)
 * (docs/services/payment-service/api-summary.md; boundaries.md's "the Refund Trigger, Reconciled").
 * Under {@code /internal/**}, deliberately unauthenticated in Phase 1 (relies on the private
 * network boundary), the same disclosed posture {@code booking-service} and
 * {@code provider-integration-service} carry.
 */
@RestController
@RequestMapping("/internal/api/v1/payments/{paymentId}/refunds")
@Tag(name = "Refunds (internal)", description = "Service-to-service refund initiation and tracking")
class RefundController {

    private final InitiateRefund initiateRefund;
    private final GetRefund getRefund;

    RefundController(InitiateRefund initiateRefund, GetRefund getRefund) {
        this.initiateRefund = initiateRefund;
        this.getRefund = getRefund;
    }

    @PostMapping
    @Operation(summary = "Initiate a refund",
            description = "Executes a booking-service-computed refund amount. Idempotent on the Idempotency-Key header.")
    ResponseEntity<InitiateRefundResponse> initiate(@PathVariable UUID paymentId,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    @Valid @RequestBody InitiateRefundRequest request) {
        Money amount = request.amount() == null ? null
                : new Money(request.amount(), Currency.getInstance(currencyOrThrow(request.currency())));
        InitiateRefund.Result result = initiateRefund.initiate(new InitiateRefund.Command(
                new PaymentId(paymentId), amount, request.reason(), new IdempotencyKey(idempotencyKey)));
        HttpStatus status = result.alreadyExisted() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(InitiateRefundResponse.from(result));
    }

    @GetMapping("/{refundId}")
    @Operation(summary = "Get a refund", description = "Track a refund's progress.")
    RefundResponse get(@PathVariable UUID paymentId, @PathVariable UUID refundId) {
        GetRefund.Result result = getRefund.get(new GetRefund.Command(new PaymentId(paymentId), new RefundId(refundId)));
        return RefundResponse.from(result.refund());
    }

    private String currencyOrThrow(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required when amount is provided");
        }
        return currency.toUpperCase();
    }
}
