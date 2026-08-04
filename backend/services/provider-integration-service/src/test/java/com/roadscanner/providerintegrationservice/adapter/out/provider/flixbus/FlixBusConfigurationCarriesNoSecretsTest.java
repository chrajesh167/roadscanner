package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural guard on the rule that gives the encrypted store its value: no FlixBus secret may
 * reach the adapter through configuration.
 *
 * <p>{@link FlixBusCredentialsTest} proves the resolver reads from {@code provider_credentials};
 * this proves there is no second route. Both are needed — a reinstated
 * {@code roadscanner.provider.flixbus.client-secret} would not fail any behavioural test, it would
 * simply sit there until someone wired it up, and a secret in a checked-in YAML file is a secret in
 * version control.
 */
class FlixBusConfigurationCarriesNoSecretsTest {

    /** Substrings that mark a configuration key as carrying a secret. */
    private static final List<String> SECRET_MARKERS =
            List.of("secret", "password", "token", "credential", "client-id", "clientid", "api-key", "apikey");

    private static Path moduleRoot() {
        Path here = Paths.get("").toAbsolutePath();
        return here.endsWith("provider-integration-service") ? here : here.resolve("provider-integration-service");
    }

    private static Stream<Path> configurationFiles() {
        try (Stream<Path> resources = Files.walk(moduleRoot().resolve("src"))) {
            return resources.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("application.*\\.(yml|yaml|properties)"))
                    .toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void flixBusPropertiesExposesNoSecretBearingComponent() {
        RecordComponent[] components = FlixBusProperties.class.getRecordComponents();

        assertThat(components).isNotEmpty();
        for (RecordComponent component : components) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertThat(SECRET_MARKERS)
                    .as("FlixBusProperties.%s looks like a credential — secrets belong in "
                            + "provider_credentials, resolved by FlixBusCredentials", component.getName())
                    .noneMatch(name::contains);
        }
    }

    @Test
    void noProfileDeclaresAFlixBusSecret() {
        configurationFiles().forEach(file -> {
            List<String> offending = flixBusKeys(readLines(file)).stream()
                    .filter(key -> SECRET_MARKERS.stream().anyMatch(key::contains))
                    .toList();

            assertThat(offending)
                    .as("%s declares a secret-looking key under roadscanner.provider.flixbus; FlixBus "
                            + "secrets must come from provider_credentials only", moduleRoot().relativize(file))
                    .isEmpty();
        });
    }

    /**
     * The keys nested under a {@code flixbus:} block, by indentation.
     *
     * <p>Scoped deliberately rather than scanning whole files: this module legitimately configures
     * unrelated secrets from the environment — the datasource password, the Redis password, the
     * credential-encryption key — and a test that flagged those would be turned off rather than
     * fixed, taking the FlixBus guarantee down with it.
     */
    private static List<String> flixBusKeys(List<String> lines) {
        List<String> keys = new java.util.ArrayList<>();
        int blockIndent = -1;
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.isBlank() || line.strip().startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (blockIndent >= 0 && indent <= blockIndent) {
                blockIndent = -1;
            }
            String key = line.strip().toLowerCase(Locale.ROOT).split(":", 2)[0].strip();
            if (blockIndent >= 0) {
                keys.add(key);
            } else if (key.equals("flixbus")) {
                blockIndent = indent;
            }
        }
        return keys;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }
}
