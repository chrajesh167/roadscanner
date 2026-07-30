package com.roadscanner.providerintegrationservice.adapter.out.security;

import com.roadscanner.providerintegrationservice.domain.exception.CredentialDecryptionException;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Local AES-256-GCM implementation of {@link CredentialCipher}.
 *
 * <p>GCM rather than CBC because it is authenticated: a tampered ciphertext fails to decrypt
 * instead of yielding plausible garbage that then gets sent to a provider as a password. A fresh
 * 12-byte IV is generated per encryption and prefixed to the output — reusing an IV under GCM is
 * catastrophic, leaking the key stream, so it must never be derived from anything stable like a
 * row id.
 *
 * <p>Stored form is {@code enc:v1:<base64(iv || ciphertext || tag)>}. The scheme marker is what
 * lets {@link #decrypt} distinguish this scheme's output from legacy plaintext and from a future
 * KMS scheme, so encryption could be introduced without rewriting existing rows and a KMS
 * migration can be incremental rather than big-bang.
 *
 * <p><strong>This holds a key in application configuration.</strong> That is appropriate for local
 * development and acceptable for a single-region deployment whose config comes from a secrets
 * manager; it is not envelope encryption and offers no key rotation, no audit trail and no
 * hardware protection. {@code KmsCredentialCipher} replacing this bean is the intended path — see
 * the port's Javadoc.
 */
public class AesGcmCredentialCipher implements CredentialCipher {

    /** Scheme marker written by this implementation. A KMS cipher would write {@code enc:kms:}. */
    public static final String SCHEME = "enc:v1:";

    /** Any value starting with this was written by *some* cipher; anything else is legacy plaintext. */
    static final String ENCRYPTED_PREFIX = "enc:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCredentialCipher(byte[] keyBytes) {
        if (keyBytes == null || (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32)) {
            throw new IllegalArgumentException(
                    "credential encryption key must be 16, 24 or 32 bytes (128/192/256-bit AES)");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return SCHEME + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // No value in the message — this is thrown on a path that is handling a secret.
            throw new IllegalStateException("Failed to encrypt a provider credential", e);
        }
    }

    @Override
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            // Written before encryption was enabled. Returned as-is so switching encryption on is
            // not a destructive migration; the next write re-stores it encrypted.
            return stored;
        }
        if (!stored.startsWith(SCHEME)) {
            throw new CredentialDecryptionException(
                    "Provider credential was written by an unsupported encryption scheme", null);
        }

        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(SCHEME.length()));
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new CredentialDecryptionException("Stored provider credential is truncated", null);
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // GCM tag mismatch (wrong key or tampering) or malformed base64. The cause carries no
            // secret material, so it is safe to attach.
            throw new CredentialDecryptionException(
                    "Failed to decrypt a provider credential — the encryption key may be wrong or rotated", e);
        }
    }

    @Override
    public String scheme() {
        return SCHEME;
    }

    /** Deliberately renders nothing about the key. */
    @Override
    public String toString() {
        return "AesGcmCredentialCipher[scheme=" + SCHEME + "]";
    }
}
