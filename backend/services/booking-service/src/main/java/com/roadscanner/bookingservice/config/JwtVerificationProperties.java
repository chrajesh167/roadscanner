package com.roadscanner.bookingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * JWT verification configuration. Public key material arrives as a PEM string sourced from
 * environment / secrets manager — the same source auth-service's own private key is sourced from
 * (docs/services/auth-service/security-design.md: "never checked into configuration or
 * source"). {@code ephemeralKeys} is the explicit, local/test-only opt-out that generates a
 * throwaway key pair instead of requiring a real one — see
 * {@code adapter.out.security.EphemeralJwtKeyPair} and {@code JwtConfig}.
 */
@ConfigurationProperties(prefix = "roadscanner.security.jwt")
public record JwtVerificationProperties(String publicKeyPem, boolean ephemeralKeys, String issuer) {

    public JwtVerificationProperties {
        Objects.requireNonNull(issuer, "roadscanner.security.jwt.issuer must be set");
    }

    public boolean hasConfiguredPublicKey() {
        return publicKeyPem != null && !publicKeyPem.isBlank();
    }
}
