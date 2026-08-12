package com.roadscanner.providerintegrationservice.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.roadscanner.providerintegrationservice.adapter.out.security.AesGcmCredentialCipher;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

    /**
     * Restart safety, stated on its own rather than as a side effect of the ephemeral-flag test.
     *
     * <p>This is the guarantee an operator actually depends on: credentials stored yesterday must
     * decrypt today. It holds only because the key comes from configuration — the ephemeral path
     * fails this by construction, which is what {@link #everyEphemeralKeyIsDistinct()} pins down.
     */
    @Test
    void aCipherRebuiltFromTheSameKeyDecryptsWhatTheLastOneWrote() {
        String key = base64Key(32);

        String storedCiphertext =
                config.credentialCipher(new CredentialEncryptionProperties(key, false)).encrypt("s3cret");
        CredentialCipher afterRestart = config.credentialCipher(new CredentialEncryptionProperties(key, false));

        assertThat(afterRestart.decrypt(storedCiphertext)).isEqualTo("s3cret");
    }

    /**
     * Startup logging is the one place the key and the plaintext could plausibly escape: it runs
     * before any redaction anyone might add later, and it is the natural spot to "just log the
     * config" while debugging. The assertion is over captured output rather than over the format
     * string, so an added argument is caught too.
     */
    @Test
    void logsThatEncryptionIsActiveWithoutRenderingTheKeyOrAnySecret() {
        String key = base64Key(32);
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);

        try {
            CredentialCipher cipher = config.credentialCipher(new CredentialEncryptionProperties(key, false));
            cipher.decrypt(cipher.encrypt("s3cret"));
        } finally {
            root.detachAppender(captured);
        }

        assertThat(captured.list).isNotEmpty();
        assertThat(captured.list)
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain(key)
                        .doesNotContain("s3cret"));
        assertThat(captured.list)
                .anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("Provider credential encryption active"));
    }

    @Test
    void reportsWhetherAKeyIsConfigured() {
        assertThat(new CredentialEncryptionProperties("abc", false).hasConfiguredKey()).isTrue();
        assertThat(new CredentialEncryptionProperties("  ", false).hasConfiguredKey()).isFalse();
        assertThat(new CredentialEncryptionProperties(null, false).hasConfiguredKey()).isFalse();
    }
}
