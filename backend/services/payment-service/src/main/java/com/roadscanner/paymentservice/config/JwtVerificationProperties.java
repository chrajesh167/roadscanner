package com.roadscanner.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/** JWT verification configuration — public key PEM sourced from a secrets manager, or an explicit
 * local/test-only ephemeral opt-out (docs/services/auth-service/security-design.md). */
@ConfigurationProperties(prefix = "roadscanner.security.jwt")
public record JwtVerificationProperties(String publicKeyPem, boolean ephemeralKeys, String issuer) {

    public JwtVerificationProperties {
        Objects.requireNonNull(issuer, "roadscanner.security.jwt.issuer must be set");
    }

    public boolean hasConfiguredPublicKey() {
        return publicKeyPem != null && !publicKeyPem.isBlank();
    }
}
