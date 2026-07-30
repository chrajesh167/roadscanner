package com.roadscanner.providerintegrationservice.domain.port.out;

/**
 * Encrypts and decrypts provider credential values at rest.
 *
 * <p>A port, not a utility class, precisely so the local AES implementation can be replaced by an
 * AWS KMS one without touching a single caller: the JPA converter, the aggregate and every test
 * depend on this interface. Swapping implementations is a bean definition
 * ({@code CredentialEncryptionConfig}), not a code change.
 *
 * <p>Two rules that make a later KMS swap safe rather than a migration:
 *
 * <ul>
 *   <li><strong>Ciphertext is self-describing.</strong> Every implementation stamps its output
 *       with a scheme marker, so {@link #decrypt} can tell "this row was written by the local
 *       cipher", "by KMS", or "before encryption existed" and route accordingly. Without that,
 *       introducing a second scheme means rewriting every row in one shot.</li>
 *   <li><strong>Decryption tolerates plaintext.</strong> Rows written before Sprint 2.1 are
 *       returned unchanged rather than throwing, so enabling encryption is not a destructive
 *       migration. Reading is lenient; writing is always encrypted.</li>
 * </ul>
 */
public interface CredentialCipher {

    /**
     * @param plaintext the raw secret; {@code null} passes through so an absent credential field
     *                  stays absent rather than becoming an encrypted empty string
     * @return self-describing ciphertext
     */
    String encrypt(String plaintext);

    /**
     * @param stored the value as held in the database, which may be ciphertext from this or an
     *               earlier scheme, or legacy plaintext
     * @return the raw secret
     * @throws com.roadscanner.providerintegrationservice.domain.exception.CredentialDecryptionException
     *         if the value carries a known scheme marker but cannot be decrypted — a wrong or
     *         rotated key. Never silently returns the ciphertext, which would send an unusable
     *         secret to a provider and surface as a confusing auth failure.
     */
    String decrypt(String stored);

    /** Identifies the scheme this implementation writes, for logging and for migration tooling. */
    String scheme();
}
