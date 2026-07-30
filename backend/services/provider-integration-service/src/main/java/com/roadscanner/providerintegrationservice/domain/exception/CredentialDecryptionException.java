package com.roadscanner.providerintegrationservice.domain.exception;

/**
 * A stored credential carries a recognised encryption marker but could not be decrypted — almost
 * always a wrong, rotated or missing key.
 *
 * <p>Fails loudly rather than returning the ciphertext. Handing raw ciphertext to a provider as if
 * it were a password produces an authentication failure that looks like bad credentials, sending
 * whoever debugs it to entirely the wrong place.
 *
 * <p>Its message deliberately names no value — not the ciphertext, not the key, not the plaintext.
 */
public class CredentialDecryptionException extends ProviderIntegrationException {

    public CredentialDecryptionException(String message, Throwable cause) {
        super(message, null, cause);
    }
}
