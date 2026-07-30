package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.adapter.out.security.AesGcmCredentialCipher;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Wires the {@link CredentialCipher}. Key loading is fail-loud, mirroring {@code JwtConfig}: a
 * cipher that quietly falls back to a default key would encrypt every deployment's secrets with a
 * value an attacker can read from the source, which is worse than no encryption because it looks
 * safe.
 *
 * <p><strong>This is the single seam for AWS KMS.</strong> Replacing the local cipher means adding
 * a {@code KmsCredentialCipher} implementing the same port and returning it from this method —
 * nothing else changes. The entity, the converter, the aggregate, the use cases and every test
 * depend only on {@link CredentialCipher}, and the stored ciphertext is scheme-tagged so rows
 * written by the old cipher keep decrypting through the transition.
 */
@Configuration
@EnableConfigurationProperties(CredentialEncryptionProperties.class)
public class CredentialEncryptionConfig {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptionConfig.class);
    private static final int EPHEMERAL_KEY_BYTES = 32;

    @Bean
    public CredentialCipher credentialCipher(CredentialEncryptionProperties properties) {
        if (properties.hasConfiguredKey()) {
            byte[] keyBytes = decodeKey(properties.key());
            log.info("Provider credential encryption active (scheme={}, keyBits={})",
                    AesGcmCredentialCipher.SCHEME, keyBytes.length * 8);
            return new AesGcmCredentialCipher(keyBytes);
        }

        if (properties.ephemeralKey()) {
            byte[] keyBytes = new byte[EPHEMERAL_KEY_BYTES];
            new SecureRandom().nextBytes(keyBytes);
            log.warn("Using an EPHEMERAL provider-credential encryption key — credentials written now "
                    + "become unreadable after a restart. Acceptable only for local development and "
                    + "tests. Set roadscanner.security.credential-encryption.key in any deployed "
                    + "environment.");
            return new AesGcmCredentialCipher(keyBytes);
        }

        throw new IllegalStateException(
                "Provider credential encryption key is not configured. Set "
                        + "roadscanner.security.credential-encryption.key (base64-encoded AES key, from the "
                        + "secrets manager), or set roadscanner.security.credential-encryption.ephemeral-key=true "
                        + "for local development/tests only.");
    }

    private static byte[] decodeKey(String base64Key) {
        try {
            return Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            // Never echo the value — it is the key.
            throw new IllegalStateException(
                    "roadscanner.security.credential-encryption.key is not valid base64", e);
        }
    }
}
