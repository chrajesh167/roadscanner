package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.adapter.out.security.AesGcmCredentialCipher;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Key loading is the basis of every credential guarantee in the service — if it silently picks a
 * wrong or default key, the encryption is decorative. So the loading rules are tested directly
 * rather than only implicitly through a passing round trip.
 */
class CredentialEncryptionConfigTest {

    private final CredentialEncryptionConfig config = new CredentialEncryptionConfig();

    private static String base64Key(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void usesTheConfiguredKeyWhenOneIsSupplied() {
        CredentialCipher cipher =
                config.credentialCipher(new CredentialEncryptionProperties(base64Key(32), false));

        assertThat(cipher.decrypt(cipher.encrypt("s3cret"))).isEqualTo("s3cret");
        assertThat(cipher.scheme()).isEqualTo(AesGcmCredentialCipher.SCHEME);
    }

    @Test
    void aConfiguredKeyWinsOverTheEphemeralFlag() {
        String key = base64Key(32);
        CredentialCipher first = config.credentialCipher(new CredentialEncryptionProperties(key, true));
        CredentialCipher second = config.credentialCipher(new CredentialEncryptionProperties(key, true));

        // Two ciphers built from the same configured key must interoperate; if the ephemeral flag
        // silently won, credentials would become unreadable after every restart in an environment
        // that had a perfectly good key configured.
        assertThat(second.decrypt(first.encrypt("s3cret"))).isEqualTo("s3cret");
    }

    @Test
    void generatesAThrowawayKeyWhenEphemeralIsExplicitlyRequested() {
        CredentialCipher cipher = config.credentialCipher(new CredentialEncryptionProperties(null, true));

        assertThat(cipher.decrypt(cipher.encrypt("s3cret"))).isEqualTo("s3cret");
    }

    @Test
    void everyEphemeralKeyIsDistinct() {
        CredentialCipher first = config.credentialCipher(new CredentialEncryptionProperties(null, true));
        CredentialCipher second = config.credentialCipher(new CredentialEncryptionProperties(null, true));

        // Which is exactly why it is local/test-only: what one process wrote, the next cannot read.
        assertThatThrownBy(() -> second.decrypt(first.encrypt("s3cret"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesToStartWithNoKeyAndNoExplicitOptOut() {
        // Fail loud. A cipher that quietly fell back to a built-in key would encrypt every
        // deployment's secrets with a value readable from source — worse than no encryption,
        // because it looks safe.
        assertThatThrownBy(() -> config.credentialCipher(new CredentialEncryptionProperties(null, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadscanner.security.credential-encryption.key")
                .hasMessageContaining("ephemeral-key");
    }

    @Test
    void treatsABlankKeyAsNoKeyAtAll() {
        assertThatThrownBy(() -> config.credentialCipher(new CredentialEncryptionProperties("   ", false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAKeyThatIsNotValidBase64WithoutEchoingIt() {
        String malformed = "not-base64-$$$-secret-looking";

        assertThatThrownBy(() -> config.credentialCipher(new CredentialEncryptionProperties(malformed, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid base64")
                // The value is the key; it must never reach a log or a stack trace.
                .hasMessageNotContaining(malformed);
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        assertThatThrownBy(() -> config.credentialCipher(new CredentialEncryptionProperties(base64Key(20), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16, 24 or 32 bytes");
    }

    @Test
    void acceptsEveryValidAesKeyLength() {
        for (int length : new int[]{16, 24, 32}) {
            CredentialCipher cipher =
                    config.credentialCipher(new CredentialEncryptionProperties(base64Key(length), false));
            assertThat(cipher.decrypt(cipher.encrypt("s3cret"))).isEqualTo("s3cret");
        }
    }

    @Test
    void reportsWhetherAKeyIsConfigured() {
        assertThat(new CredentialEncryptionProperties("abc", false).hasConfiguredKey()).isTrue();
        assertThat(new CredentialEncryptionProperties("  ", false).hasConfiguredKey()).isFalse();
        assertThat(new CredentialEncryptionProperties(null, false).hasConfiguredKey()).isFalse();
    }
}
