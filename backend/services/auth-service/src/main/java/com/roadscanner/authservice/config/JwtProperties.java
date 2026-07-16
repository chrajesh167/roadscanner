package com.roadscanner.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * JWT issuance configuration. Key material arrives as PEM strings sourced from environment /
 * secrets manager (docs/services/auth-service/security-design.md: "never checked into
 * configuration or source") — {@code ephemeralKeys} is the explicit, local/test-only opt-out
 * that generates a throwaway pair instead; production profiles never set it, so a missing key
 * fails startup loudly (see {@link JwtConfig}).
 */
@ConfigurationProperties(prefix = "roadscanner.security.jwt")
public record JwtProperties(
        String privateKeyPem,
        String publicKeyPem,
        boolean ephemeralKeys,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {

    public JwtProperties {
        Objects.requireNonNull(issuer, "roadscanner.security.jwt.issuer must be set");
        Objects.requireNonNull(accessTokenTtl, "roadscanner.security.jwt.access-token-ttl must be set");
        Objects.requireNonNull(refreshTokenTtl, "roadscanner.security.jwt.refresh-token-ttl must be set");
    }

    public boolean hasConfiguredKeys() {
        return privateKeyPem != null && !privateKeyPem.isBlank()
                && publicKeyPem != null && !publicKeyPem.isBlank();
    }
}
