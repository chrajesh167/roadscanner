package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Non-secret configuration for the FlixBus adapter — {@code roadscanner.provider.flixbus.*}.
 *
 * <p><strong>Carries no credentials.</strong> Partner secrets live in {@code provider_credentials},
 * encrypted, and are resolved through {@link FlixBusCredentials}. Leaving a second, config-shaped
 * source of the same secret would mean an operator rotating it through the admin API kept
 * authenticating with the stale copy until someone redeployed.
 * {@code FlixBusConfigurationCarriesNoSecretsTest} enforces this.
 *
 * <p>The values here are the operational knobs the documented API leaves to the caller: the
 * currency to quote in, the calling code for phone numbers that arrive without one, how long a
 * partner session is reused, how the order is polled while FlixBus materializes it, and how
 * settlement is declared. They are configuration rather than constants because they are business
 * decisions — a second market means a different currency and calling code, not a code change.
 *
 * @param sessionTtl          how long a partner session token is reused before re-login. The API
 *                            reference states sessions last roughly 24 hours; this is held slightly
 *                            under that so a token is replaced before FlixBus rejects it rather
 *                            than after a traveller's booking call fails.
 * @param checkoutPollAttempts how many times to re-read a checkout while waiting for the order to
 *                            appear. FlixBus does not materialize the order synchronously.
 * @param checkoutPollDelay   the wait between those reads.
 * @param paymentProvider     the {@code psp} declared at payment.
 * @param paymentMethod       the {@code method} declared at payment.
 */
@ConfigurationProperties(prefix = "roadscanner.provider.flixbus")
public record FlixBusProperties(String baseUrl, Duration connectTimeout, Duration readTimeout, String currency,
                                String defaultCallingCode, Duration sessionTtl, int checkoutPollAttempts,
                                Duration checkoutPollDelay, String paymentProvider, String paymentMethod) {

    public FlixBusProperties {
        currency = orDefault(currency, "INR");
        defaultCallingCode = orDefault(defaultCallingCode, "+91");
        connectTimeout = orDefault(connectTimeout, Duration.ofSeconds(5));
        readTimeout = orDefault(readTimeout, Duration.ofSeconds(15));
        sessionTtl = orDefault(sessionTtl, Duration.ofHours(23));
        checkoutPollAttempts = checkoutPollAttempts > 0 ? checkoutPollAttempts : 3;
        checkoutPollDelay = orDefault(checkoutPollDelay, Duration.ofSeconds(2));

        // Settlement is out-of-band for a B2B partner: RoadScanner has already collected the fare,
        // and tells FlixBus so rather than passing card details through it.
        paymentProvider = orDefault(paymentProvider, "offline");
        paymentMethod = orDefault(paymentMethod, "cash");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
