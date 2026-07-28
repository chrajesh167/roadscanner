package com.roadscanner.paymentservice.config;

import com.roadscanner.paymentservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.paymentservice.adapter.out.security.JwtDecoderKeyMaterial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;

/** Wires JWT verification, fail-loud — no configured key and no explicit ephemeral opt-in means the
 * application refuses to start (docs/services/auth-service/security-design.md). */
@Configuration
@EnableConfigurationProperties(JwtVerificationProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "roadscanner.security.jwt", name = "ephemeral-keys", havingValue = "true")
    public EphemeralJwtKeyPair ephemeralJwtKeyPair() {
        EphemeralJwtKeyPair keyPair = EphemeralJwtKeyPair.generate();
        log.warn("Using an EPHEMERAL RS256 verification key (kid={}) — acceptable only for local "
                + "development and tests.", keyPair.keyId());
        return keyPair;
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtVerificationProperties properties, ObjectProvider<EphemeralJwtKeyPair> ephemeral) {
        RSAPublicKey publicKey;
        if (properties.ephemeralKeys()) {
            publicKey = ephemeral.getObject().publicKey();
        } else if (properties.hasConfiguredPublicKey()) {
            JwtDecoderKeyMaterial keyMaterial = JwtDecoderKeyMaterial.fromPem(properties.publicKeyPem());
            log.info("Loaded configured RS256 verification key (kid={})", keyMaterial.keyId());
            publicKey = keyMaterial.publicKey();
        } else {
            throw new IllegalStateException(
                    "JWT verification key is not configured. Set roadscanner.security.jwt.public-key-pem "
                            + "(matching auth-service's signing key), or roadscanner.security.jwt.ephemeral-keys=true "
                            + "for local development/tests only.");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
