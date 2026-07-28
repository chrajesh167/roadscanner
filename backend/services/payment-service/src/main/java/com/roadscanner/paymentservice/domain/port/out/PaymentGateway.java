package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.GatewayWebhookEvent;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.RefundId;

/**
 * The outbound port every external payment gateway is reached through — the canonical,
 * gateway-agnostic interface the application layer depends on. Every method speaks only in this
 * service's own value objects, <strong>never</strong> a gateway-specific type
 * (docs/services/payment-service/domain-model.md's "Payment Gateway Abstraction"). Concrete
 * per-gateway adapters (Razorpay/Stripe/Adyen) are the only classes that import a gateway SDK;
 * they translate to and from these types at the boundary, and translate gateway errors into this
 * service's canonical {@code PaymentGatewayException} hierarchy.
 *
 * <p>The domain never knows which implementation is in use — {@link PaymentGatewayRegistry}
 * resolves one from a {@link GatewayType} at runtime.
 */
public interface PaymentGateway {

    /** Which gateway this adapter integrates — the registry key. */
    GatewayType type();

    /** Starts a charge transaction with the gateway, returning its opaque references. For
     * asynchronous methods (UPI, some wallets) the actual capture arrives later as a webhook. */
    GatewayReference initiateCharge(ChargeRequest request);

    /** Requests a refund of {@code amount} against the original payment's gateway reference. The
     * refund's completion is confirmed later by a webhook. */
    GatewayReference refund(RefundRequest request);

    /** Verifies the webhook signature against this gateway's signing secret. A failed verification
     * causes the webhook to be audited and rejected, never applied. */
    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);

    /** Translates this gateway's raw webhook payload into the canonical {@link GatewayWebhookEvent}
     * — no instrument-adjacent data (NFR-12) crosses this boundary. */
    GatewayWebhookEvent parseWebhook(String rawPayload);

    record ChargeRequest(PaymentId paymentId, Money amount, PaymentMethod method,
                         BookingReference bookingReference) {
    }

    record RefundRequest(RefundId refundId, PaymentId paymentId, GatewayReference paymentGatewayReference,
                         Money amount) {
    }
}
