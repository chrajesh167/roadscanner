package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The containment rule: <strong>no provider-specific or HTTP-client type may leave this
 * service.</strong>
 *
 * <p>Search-service and every future caller must see only provider-neutral failures. If a
 * {@code RestClientException} or a FlixBus type escaped, every caller would need to know FlixBus's
 * error vocabulary — which is exactly the coupling this service exists to prevent, and swapping
 * providers would then be a change scattered across services rather than one adapter package.
 *
 * <p>This is enforced structurally rather than by convention: the source of every outbound port
 * and every domain exception is scanned, so a future adapter cannot quietly widen a signature.
 */
class ProviderExceptionContainmentTest {

    private static final Path DOMAIN = Path.of("src/main/java/com/roadscanner/providerintegrationservice/domain");
    private static final Path FLIXBUS =
            Path.of("src/main/java/com/roadscanner/providerintegrationservice/adapter/out/provider/flixbus");

    /** Anything naming one of these in a domain type would leak provider vocabulary outward. */
    private static final List<String> FORBIDDEN_IN_DOMAIN = List.of(
            "org.springframework.web.client",
            "FlixBus",
            "com.fasterxml.jackson");

    private static Stream<Path> javaFilesUnder(Path root) throws IOException {
        return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
    }

    /**
     * Strips comments before scanning. Naming FlixBus in a Javadoc line is not a leak — it is how
     * the design gets explained. Referencing the <em>type</em> is. Without this the test would
     * punish documentation, and the usual response to that is to delete the documentation.
     */
    private static String codeOnly(Path path) {
        try {
            return Files.readString(path)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("(?m)//.*$", "");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    @Test
    void noDomainTypeReferencesAProviderSpecificOrHttpClientType() throws IOException {
        try (Stream<Path> files = javaFilesUnder(DOMAIN)) {
            List<String> offenders = files
                    .filter(path -> {
                        String source = codeOnly(path);
                        return FORBIDDEN_IN_DOMAIN.stream().anyMatch(source::contains);
                    })
                    .map(Path::toString)
                    .toList();

            assertThat(offenders)
                    .as("domain types must not name FlixBus, Spring's HTTP client, or Jackson — "
                            + "a provider's vocabulary must not reach a caller")
                    .isEmpty();
        }
    }

    @Test
    void everyDomainExceptionExtendsTheNeutralRoot() throws IOException {
        try (Stream<Path> files = javaFilesUnder(DOMAIN.resolve("exception"))) {
            List<String> offenders = files
                    .filter(path -> !path.getFileName().toString().equals("ProviderIntegrationException.java"))
                    .filter(path -> !codeOnly(path).contains("extends ProviderIntegrationException"))
                    .map(path -> path.getFileName().toString())
                    .toList();

            // One root means one handler mapping and one thing for a caller to catch.
            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void everyFlixBusClassIsPackagePrivateOrConfined() throws IOException {
        try (Stream<Path> files = javaFilesUnder(FLIXBUS)) {
            List<String> publicTypes = files
                    .filter(path -> {
                        String source = codeOnly(path);
                        return source.contains("\npublic class ") || source.contains("\npublic interface ")
                                || source.contains("\npublic enum ");
                    })
                    .map(path -> path.getFileName().toString())
                    // Properties is bound by Spring and must be public; it carries no exception type.
                    .filter(name -> !name.equals("FlixBusProperties.java"))
                    .toList();

            assertThat(publicTypes)
                    .as("FlixBus classes stay package-private so nothing outside the adapter can name them")
                    .isEmpty();
        }
    }

    @Test
    void theNeutralExceptionsCarryTheRetryabilityTheExecutionLayerReads() {
        // The retry strategy reads ProviderError.retryable, so these classifications are what
        // actually decide whether a failure is repeated. Pinned here because getting one wrong is
        // silent: it just means needless load, or a stubbornly un-retried transient failure.
        assertThat(new ProviderTimeoutException(ProviderType.FLIXBUS, "search", Duration.ofSeconds(1), null)
                .error().retryable()).isTrue();
        assertThat(new RateLimitedException(ProviderType.FLIXBUS, "search", Duration.ofSeconds(1), null)
                .error().retryable()).isTrue();
        assertThat(new ProviderValidationException(ProviderType.FLIXBUS, "search", "bad date", null)
                .error().retryable()).isFalse();
        assertThat(new ProviderResponseException(ProviderType.FLIXBUS, "search", "unparseable", null)
                .error().retryable()).isFalse();
    }

    @Test
    void neutralExceptionsNameTheProviderAndOperationForDiagnosis() {
        ProviderTimeoutException timeout =
                new ProviderTimeoutException(ProviderType.FLIXBUS, "search", Duration.ofMillis(750), null);

        assertThat(timeout).hasMessageContaining("FLIXBUS").hasMessageContaining("search")
                .hasMessageContaining("750");
        assertThat(timeout.timeout()).isEqualTo(Duration.ofMillis(750));
    }

    @Test
    void rateLimitedExceptionCarriesTheProvidersRetryAfterWhenGiven() {
        assertThat(new RateLimitedException(ProviderType.FLIXBUS, "search", Duration.ofSeconds(30), null)
                .retryAfter()).contains(Duration.ofSeconds(30));
        // Absent is a legitimate answer — not every provider tells us.
        assertThat(new RateLimitedException(ProviderType.FLIXBUS, "search", null, null).retryAfter()).isEmpty();
    }

    @Test
    void validationAndResponseExceptionsFallBackToASafeMessage() {
        assertThat(new ProviderValidationException(ProviderType.FLIXBUS, "search", "  ", null).error().message())
                .isEqualTo("The provider rejected the request as invalid");
        assertThat(new ProviderResponseException(ProviderType.FLIXBUS, "search", null, null).error().message())
                .isEqualTo("The provider returned an unusable response");
    }
}
