package com.roadscanner.authservice.config;

import com.roadscanner.authservice.adapter.out.security.BCryptPasswordHasherAdapter;
import com.roadscanner.authservice.adapter.out.security.JwtKeyMaterial;
import com.roadscanner.authservice.adapter.out.security.JwtTokenSignerAdapter;
import com.roadscanner.authservice.domain.port.out.TokenSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the crypto adapters that need configuration values: signing-key material, the RS256
 * token signer, and the BCrypt password hasher. Key loading is fail-loud: no configured PEM
 * pair and no explicit ephemeral opt-in means the application refuses to start — a signing
 * service silently running without its keys is exactly the misconfiguration
 * docs/services/auth-service/logging-observability.md's health section warns about.
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public JwtKeyMaterial jwtKeyMaterial(JwtProperties properties) {
        if (properties.hasConfiguredKeys()) {
            JwtKeyMaterial keyMaterial = JwtKeyMaterial.fromPem(
                    properties.privateKeyPem(), properties.publicKeyPem());
            log.info("Loaded configured RS256 signing key material (kid={})", keyMaterial.keyId());
            return keyMaterial;
        }
        if (properties.ephemeralKeys()) {
            JwtKeyMaterial keyMaterial = JwtKeyMaterial.ephemeral();
            log.warn("Using EPHEMERAL RS256 signing keys (kid={}) — tokens will not survive a restart. "
                    + "This is acceptable only for local development and tests.", keyMaterial.keyId());
            return keyMaterial;
        }
        throw new IllegalStateException(
                "JWT signing keys are not configured. Set roadscanner.security.jwt.private-key-pem and "
                        + ".public-key-pem (from the secrets manager), or set "
                        + "roadscanner.security.jwt.ephemeral-keys=true for local development only.");
    }

    @Bean
    public TokenSigner tokenSigner(JwtKeyMaterial keyMaterial, JwtProperties properties) {
        return new JwtTokenSignerAdapter(keyMaterial, properties.issuer());
    }

    @Bean
    public BCryptPasswordHasherAdapter passwordHasher(AuthProperties properties) {
        return new BCryptPasswordHasherAdapter(properties.bcryptStrength());
    }
}
