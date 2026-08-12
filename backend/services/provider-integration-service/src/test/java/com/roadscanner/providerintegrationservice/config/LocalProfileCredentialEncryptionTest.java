package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Binds the real {@code application-local.yml} rather than a fixture.
 *
 * <p>The unit tests over {@link CredentialEncryptionConfig} prove the loading <em>rules</em>; this
 * proves the local profile actually asks for the rule we want. Those are different failures: the
 * rules were always correct, and the local profile still lost every credential on restart because
 * it set {@code ephemeral-key: true} and never consulted the environment.
 *
 * <p>A developer storing a real partner token had it decrypt normally until the next restart and
 * then never again — the row survived, the key did not. Nothing failed loudly, so the data loss was
 * indistinguishable from the provider rejecting the credentials. Asserting against the shipped file
 * is what keeps that from being reintroduced by a one-line edit.
 */
class LocalProfileCredentialEncryptionTest {

    private static final String KEY_PROPERTY = "roadscanner.security.credential-encryption.key";
    private static final String EPHEMERAL_PROPERTY = "roadscanner.security.credential-encryption.ephemeral-key";
    private static final String KEY_ENV_VARIABLE = "PROVIDER_CREDENTIAL_ENCRYPTION_KEY";

    private final CredentialEncryptionConfig config = new CredentialEncryptionConfig();

    /**
     * Loads the shipped local profile into an environment where {@code PROVIDER_CREDENTIAL_ENCRYPTION_KEY}
     * holds the supplied value, resolving placeholders exactly as Spring Boot would at startup.
     */
    private static CredentialEncryptionProperties bindLocalProfile(String keyEnvValue) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        if (keyEnvValue != null) {
            environment.setProperty(KEY_ENV_VARIABLE, keyEnvValue);
        }

        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-local", new ClassPathResource("application-local.yml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        return Binder.get(environment)
                .bind("roadscanner.security.credential-encryption", CredentialEncryptionProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "application-local.yml declares no credential-encryption block"));
    }

    private static String base64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void localProfileNeverEnablesAnEphemeralKey() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-local", new ClassPathResource("application-local.yml"));

        // Asserted on the raw file, not the bound record: binding would report `false` for a key
        // that is merely absent, which is the same value we want and would hide a re-added `true`.
        assertThat(sources)
                .allSatisfy(source -> assertThat(source.getProperty(EPHEMERAL_PROPERTY))
                        .as("application-local.yml must not opt into a throwaway credential key — "
                                + "it discards every stored credential on restart")
                        .isNull());
    }

    @Test
    void localProfileTakesItsKeyFromTheEnvironmentVariable() throws IOException {
        String key = base64Key();

        CredentialEncryptionProperties properties = bindLocalProfile(key);

        assertThat(properties.hasConfiguredKey()).isTrue();
        assertThat(properties.ephemeralKey()).isFalse();
        assertThat(properties.key()).isEqualTo(key);
    }

    @Test
    void localProfileWithTheKeySetProducesAUsableCipher() throws IOException {
        CredentialEncryptionProperties properties = bindLocalProfile(base64Key());

        CredentialCipher cipher = config.credentialCipher(properties);

        String ciphertext = cipher.encrypt("partner-token");
        assertThat(ciphertext).doesNotContain("partner-token");
        assertThat(cipher.decrypt(ciphertext)).isEqualTo("partner-token");
    }

    @Test
    void localProfileSurvivesARestartWhenTheKeyStaysTheSame() throws IOException {
        String key = base64Key();

        // Two independent bind-and-build cycles from the same environment value: what the process
        // before the restart wrote, the process after it must still read. This is the property the
        // ephemeral key silently broke.
        CredentialCipher beforeRestart = config.credentialCipher(bindLocalProfile(key));
        String storedCiphertext = beforeRestart.encrypt("partner-token");

        CredentialCipher afterRestart = config.credentialCipher(bindLocalProfile(key));

        assertThat(afterRestart.decrypt(storedCiphertext)).isEqualTo("partner-token");
    }

    @Test
    void localProfileFailsLoudlyWhenTheEnvironmentVariableIsUnset() throws IOException {
        CredentialEncryptionProperties properties = bindLocalProfile(null);

        // The placeholder's empty default leaves a blank key, and the local profile no longer opts
        // into an ephemeral one — so this must stop startup rather than mint a key that quietly
        // discards tomorrow's data.
        assertThat(properties.hasConfiguredKey()).isFalse();
        assertThat(properties.ephemeralKey()).isFalse();

        assertThatThrownBy(() -> config.credentialCipher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KEY_PROPERTY);
    }
}
