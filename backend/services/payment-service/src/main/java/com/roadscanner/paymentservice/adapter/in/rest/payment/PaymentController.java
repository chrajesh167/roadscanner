package com.roadscanner.paymentservice.adapter.in.rest.payment;

import com.roadscanner.paymentservice.adapter.in.rest.RequesterContextResolver;
import com.roadscanner.paymentservice.config.PaymentProperties;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.RequesterContext;
import com.roadscanner.paymentservice.domain.model.Role;
import com.roadscanner.paymentservice.domain.port.in.GetPayment;
import com.roadscanner.paymentservice.domain.port.in.GetPaymentStatus;
import com.roadscanner.paymentservice.domain.port.in.InitiatePayment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.UUID;

/** {@code Initiate Payment} / {@code Get Payment} / {@code Get Payment Status} — the client-facing
 * surface, reached through {@code api-gateway} (docs/services/payment-service/api-summary.md). */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment initiation and status for a pending booking")
class PaymentController {

    private final InitiatePayment initiatePayment;
    private final GetPayment getPayment;
    private final GetPaymentStatus getPaymentStatus;
    private final PaymentProperties properties;

    PaymentController(InitiatePayment initiatePayment, GetPayment getPayment, GetPaymentStatus getPaymentStatus,
                      PaymentProperties properties) {
        this.initiatePayment = initiatePayment;
        this.getPayment = getPayment;
        this.getPaymentStatus = getPaymentStatus;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Initiate a payment",
            description = "Starts payment for a PENDING_PAYMENT booking. Idempotent on the Idempotency-Key header.")
    ResponseEntity<InitiatePaymentResponse> initiate(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody InitiatePaymentRequest request) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        if (requester.role() != Role.TRAVELER) {
            throw new AccessDeniedException("Only travelers may initiate a payment");
        }
        GatewayType gatewayType = request.gatewayType() != null && !request.gatewayType().isBlank()
                ? new GatewayType(request.gatewayType())
                : new GatewayType(properties.gateways().defaultType());
        Money amount = new Money(request.amount(), Currency.getInstance(request.currency().toUpperCase()));
        InitiatePayment.Result result = initiatePayment.initiate(new InitiatePayment.Command(
                new com.roadscanner.paymentservice.domain.model.BookingReference(request.bookingReference()),
                requester.requesterId(), amount, request.method(), gatewayType, new IdempotencyKey(idempotencyKey)));
        HttpStatus status = result.alreadyExisted() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(InitiatePaymentResponse.from(result));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment", description = "Ownership-checked — a denied request reads as 404, not 403.")
    PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        GetPayment.Result result = getPayment.get(new GetPayment.Command(new PaymentId(paymentId), requester));
        return PaymentResponse.from(result.payment());
    }

    @GetMapping("/{paymentId}/status")
    @Operation(summary = "Get payment status", description = "Poll an in-flight (async) payment for its outcome.")
    PaymentStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        GetPaymentStatus.Result result = getPaymentStatus.get(
                new GetPaymentStatus.Command(new PaymentId(paymentId), requester));
        return PaymentStatusResponse.from(result);
    }
}
