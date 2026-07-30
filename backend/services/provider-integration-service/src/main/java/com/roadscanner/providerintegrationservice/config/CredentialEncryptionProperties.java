package com.roadscanner.providerintegrationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credential-encryption configuration.
 *
 * <p>{@code key} is a base64-encoded AES key (16, 24 or 32 bytes) sourced from the environment or
 * a secrets manager — never checked into configuration, the same rule
 * {@code JwtVerificationProperties} follows for the JWT public key.
 *
 * <p>{@code ephemeralKey} is the explicit local/test-only opt-out: a throwaway key generated at
 * startup. Credentials written under it cannot be read after a restart, which is exactly right for
 * a developer machine and catastrophic anywhere else — hence the opt-out is explicit rather than a
 * silent fallback, and {@code CredentialEncryptionConfig} refuses to start if neither is supplied.
 *
 * <p>There is deliberately no "disabled" mode. An encryption layer that can be switched off is one
 * misconfiguration away from silently writing plaintext secrets, and the read path already
 * tolerates pre-encryption rows, so nothing needs it.
 */
@ConfigurationProperties(prefix = "roadscanner.security.credential-encryption")
public record CredentialEncryptionProperties(String key, boolean ephemeralKey) {

    public boolean hasConfiguredKey() {
        return key != null && !key.isBlank();
    }
}
