package com.roadscanner.providerintegrationservice.adapter.out.security;

import com.roadscanner.providerintegrationservice.domain.exception.CredentialDecryptionException;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The cipher's own guarantees — round trip, IV freshness, tamper detection, and the leniency that
 * makes enabling encryption non-destructive. */
class AesGcmCredentialCipherTest {

    private static byte[] key(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private final CredentialCipher cipher = new AesGcmCredentialCipher(key(32));

    @Test
    void roundTripsASecret() {
        String encrypted = cipher.encrypt("s3cret-value");

        assertThat(encrypted).isNotEqualTo("s3cret-value");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("s3cret-value");
    }

    @Test
    void ciphertextIsSelfDescribing() {
        assertThat(cipher.encrypt("s3cret")).startsWith(AesGcmCredentialCipher.SCHEME);
        assertThat(cipher.scheme()).isEqualTo(AesGcmCredentialCipher.SCHEME);
    }

    @Test
    void ciphertextNeverContainsThePlaintext() {
        String encrypted = cipher.encrypt("s3cret-value");

        assertThat(encrypted).doesNotContain("s3cret-value");
        assertThat(Base64.getEncoder().encodeToString("s3cret-value".getBytes())).isNotEqualTo(encrypted);
    }

    @Test
    void producesADifferentCiphertextEveryTimeForTheSameInput() {
        // A fresh IV per encryption. Reusing one under GCM leaks the key stream, and identical
        // ciphertexts would also tell an observer which providers share a password.
        assertThat(IntStream.range(0, 20).mapToObj(i -> cipher.encrypt("same-value")).distinct().count())
                .isEqualTo(20);
    }

    @Test
    void passesNullThroughSoAnAbsentFieldStaysAbsent() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void roundTripsAnEmptyStringAndUnicode() {
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
        assertThat(cipher.decrypt(cipher.encrypt("pässwörd–✓"))).isEqualTo("pässwörd–✓");
    }

    @Test
    void returnsLegacyPlaintextUnchanged() {
        // Rows written before encryption existed must keep working — this leniency is what makes
        // turning encryption on a non-destructive change rather than a data migration.
        assertThat(cipher.decrypt("legacy-plaintext-password")).isEqualTo("legacy-plaintext-password");
    }

    @Test
    void rejectsCiphertextEncryptedUnderADifferentKey() {
        String encrypted = new AesGcmCredentialCipher(key(32)).encrypt("s3cret");

        // GCM authenticates: a wrong key fails outright rather than yielding plausible garbage
        // that would then be sent to a provider as a password.
        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(CredentialDecryptionException.class)
                .hasMessageContaining("key may be wrong or rotated");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("s3cret");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(CredentialDecryptionException.class);
    }

    @Test
    void rejectsTruncatedCiphertext() {
        String tooShort = AesGcmCredentialCipher.SCHEME + Base64.getEncoder().encodeToString(new byte[4]);

        assertThatThrownBy(() -> cipher.decrypt(tooShort)).isInstanceOf(CredentialDecryptionException.class);
    }

    @Test
    void rejectsAnUnknownEncryptionScheme() {
        // A row written by a future KMS cipher must not be mistaken for plaintext and handed to a
        // provider verbatim.
        assertThatThrownBy(() -> cipher.decrypt("enc:kms:AAAA"))
                .isInstanceOf(CredentialDecryptionException.class)
                .hasMessageContaining("unsupported encryption scheme");
    }

    @Test
    void acceptsEveryValidAesKeyLength() {
        for (int length : new int[]{16, 24, 32}) {
            CredentialCipher sized = new AesGcmCredentialCipher(key(length));
            assertThat(sized.decrypt(sized.encrypt("s3cret"))).isEqualTo("s3cret");
        }
    }

    @Test
    void rejectsAnInvalidKeyLength() {
        assertThatThrownBy(() -> new AesGcmCredentialCipher(key(20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16, 24 or 32 bytes");
        assertThatThrownBy(() -> new AesGcmCredentialCipher(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverRendersTheKey() {
        assertThat(cipher.toString()).contains("AesGcmCredentialCipher").doesNotContain("key");
    }
}
