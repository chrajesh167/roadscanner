package com.roadscanner.providerintegrationservice;

import com.roadscanner.providerintegrationservice.adapter.in.rest.admin.ProviderCredentialsRequest;
import com.roadscanner.providerintegrationservice.adapter.out.security.AesGcmCredentialCipher;
import com.roadscanner.providerintegrationservice.domain.exception.CredentialDecryptionException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every way a partner secret could escape, asserted shut.
 *
 * <p>Credential leaks are not caught by ordinary tests: a password rendered into a log line still
 * produces a passing test and a working feature. It shows up months later in a log aggregator that
 * a dozen people can read. So each escape route gets an explicit assertion here rather than relying
 * on reviewers noticing.
 *
 * <p>The routes: {@code toString()}, exception messages, REST responses, OpenAPI, and — the one
 * that actually bit — the {@code toString()} Java generates for a record whose components include
 * secrets.
 */
class CredentialExposureTest {

    private static final String PASSWORD = "s3cret-PASSWORD-value";
    private static final String TOKEN = "s3cret-TOKEN-value";
    private static final String EMAIL = "partner@roadscanner.com";

    private static final Path MAIN = Path.of("src/main/java/com/roadscanner/providerintegrationservice");

    private static ProviderCredentials credentials() {
        return ProviderCredentials.issue(ProviderCredentialsId.generate(), ProviderId.generate(),
                EMAIL, PASSWORD, TOKEN, Instant.parse("2026-07-30T12:00:00Z"));
    }

    // ---------- toString ----------

    @Test
    void theAggregateRendersNoSecret() {
        assertThat(credentials().toString())
                .doesNotContain(PASSWORD, TOKEN, EMAIL)
                .contains("password=set", "token=set");
    }

    @Test
    void theRestRequestRecordRendersNoSecret() {
        // A record's generated toString prints every component. Without an override this returns
        // "ProviderCredentialsRequest[partnerEmail=..., partnerPassword=s3cret-PASSWORD-value, ...]"
        // and one log.debug of the bound request body writes a partner password to disk.
        ProviderCredentialsRequest request = new ProviderCredentialsRequest(EMAIL, PASSWORD, TOKEN);

        assertThat(request.toString()).doesNotContain(PASSWORD, TOKEN, EMAIL);
        // Presence is still reported — enough to debug a failed credential write.
        assertThat(request.toString()).contains("password=set", "token=set");
    }

    @Test
    void theApplicationCommandRecordRendersNoSecret() {
        var command = new ManageProviderCredentials.StoreCredentialsCommand(
                ProviderId.generate(), EMAIL, PASSWORD, TOKEN);

        assertThat(command.toString()).doesNotContain(PASSWORD, TOKEN, EMAIL);
    }

    @Test
    void anAbsentSecretIsReportedAsAbsentRatherThanNull() {
        assertThat(new ProviderCredentialsRequest(null, null, TOKEN).toString())
                .contains("password=absent", "token=set");
    }

    @Test
    void theCipherRendersNoKey() {
        assertThat(new AesGcmCredentialCipher(new byte[32]).toString()).doesNotContain("key=");
    }

    // ---------- exception messages ----------

    @Test
    void theDomainRejectionNamesNoValue() {
        assertThatThrownBy(() -> ProviderCredentials.issue(ProviderCredentialsId.generate(),
                ProviderId.generate(), EMAIL, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                // Names the fields, never their contents.
                .hasMessageContaining("partnerPassword")
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    void aDecryptionFailureNamesNoCiphertextOrKey() {
        CredentialCipher cipher = new AesGcmCredentialCipher(new byte[32]);
        String foreign = new AesGcmCredentialCipher("another-key-0123456789012345678!".getBytes())
                .encrypt(PASSWORD);

        assertThatThrownBy(() -> cipher.decrypt(foreign))
                .isInstanceOf(CredentialDecryptionException.class)
                .hasMessageNotContaining(PASSWORD)
                .hasMessageNotContaining(foreign);
    }

    @Test
    void aDecryptionFailureStackTraceCarriesNoPlaintext() {
        CredentialCipher cipher = new AesGcmCredentialCipher(new byte[32]);

        // The cause is attached for diagnosis, so the whole chain must be clean — not just the
        // top-level message, which is what a naive check would look at.
        assertThatThrownBy(() -> cipher.decrypt("enc:v1:not-valid-base64-$$$"))
                .isInstanceOf(CredentialDecryptionException.class)
                .hasStackTraceContaining("decrypt")
                .satisfies(e -> assertThat(stackTraceOf(e)).doesNotContain(PASSWORD, TOKEN));
    }

    private static String stackTraceOf(Throwable t) {
        java.io.StringWriter writer = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    // ---------- REST responses and OpenAPI ----------

    @Test
    void theResponseTypeHasNoFieldThatCouldHoldASecret() throws IOException {
        String source = Files.readString(
                MAIN.resolve("adapter/in/rest/admin/ProviderCredentialsResponse.java"));

        // Stronger than asserting a particular response body: the type structurally cannot carry a
        // secret, so no future change to the mapper can start returning one.
        assertThat(source).doesNotContain("String partnerPassword", "String partnerToken",
                "String partnerEmail");
        assertThat(source).contains("boolean hasPassword", "boolean hasToken");
    }

    @Test
    void noOpenApiExampleContainsASecretValue() throws IOException {
        // @Schema(example = "...") values are published in the OpenAPI document and rendered in
        // Swagger UI, so a realistic-looking password there becomes a copy-paste credential.
        List<String> offenders = adminRestSources()
                .flatMap(path -> secretFieldsWithAnExample(path).stream()
                        .map(field -> path.getFileName() + "#" + field))
                .toList();

        assertThat(offenders)
                .as("no secret field may declare an OpenAPI example")
                .isEmpty();
    }

    @Test
    void theCredentialsRequestDocumentsItsFieldsAsWriteOnly() throws IOException {
        String source = Files.readString(
                MAIN.resolve("adapter/in/rest/admin/ProviderCredentialsRequest.java"));

        assertThat(source).contains("never returned by any endpoint");
    }

    // ---------- logging ----------

    @Test
    void noLogStatementInterpolatesACredentialAccessor() throws IOException {
        // Catches log.info("...{}", credentials.partnerPassword()) and friends anywhere in main.
        List<String> offenders = mainSources()
                .filter(path -> {
                    String source = readQuietly(path);
                    return source.lines().anyMatch(line -> {
                        String trimmed = line.trim();
                        boolean isLogCall = trimmed.startsWith("log.") || trimmed.contains("logger.");
                        return isLogCall && (trimmed.contains("partnerPassword")
                                || trimmed.contains("partnerToken")
                                || trimmed.contains("getPartnerPassword")
                                || trimmed.contains("getPartnerToken"));
                    });
                })
                .map(path -> path.getFileName().toString())
                .toList();

        assertThat(offenders).as("no log statement may interpolate a credential").isEmpty();
    }

    @Test
    void noLogStatementLogsAWholeCredentialCarryingObject() throws IOException {
        // Even with toString overridden, this keeps the intent explicit: credentials are logged by
        // presence, never by object.
        List<String> offenders = mainSources()
                .filter(path -> {
                    String source = readQuietly(path);
                    return source.lines().anyMatch(line -> {
                        String trimmed = line.trim();
                        return trimmed.startsWith("log.")
                                && (trimmed.contains(", credentials)") || trimmed.contains(", request)"));
                    });
                })
                .map(path -> path.getFileName().toString())
                .toList();

        assertThat(offenders).isEmpty();
    }

    /**
     * Finds secret-carrying components whose own {@code @Schema} annotation declares an example.
     * Checked per field rather than per file: a legitimate example on {@code partnerEmail} in the
     * same record must not be reported, or the test gets muted as noise.
     */
    private static List<String> secretFieldsWithAnExample(Path path) {
        List<String> lines = readQuietly(path).lines().toList();
        List<String> offenders = new java.util.ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("String partnerPassword") && !line.startsWith("String partnerToken")) {
                continue;
            }
            // Walk back over this component's annotation lines, stopping at the previous component.
            for (int j = i - 1; j >= 0 && !lines.get(j).trim().startsWith("String "); j--) {
                if (lines.get(j).contains("example =")) {
                    offenders.add(line.replace(",", "").replace("String ", ""));
                    break;
                }
            }
        }
        return offenders;
    }

    private static Stream<Path> mainSources() throws IOException {
        return Files.walk(MAIN).filter(path -> path.toString().endsWith(".java"));
    }

    private static Stream<Path> adminRestSources() throws IOException {
        return Files.walk(MAIN.resolve("adapter/in/rest/admin"))
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
