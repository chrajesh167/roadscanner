package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one place the FlixBus adapter is allowed to obtain a secret, tested on its own.
 *
 * <p>Two properties matter here and are easy to regress: secrets come from
 * {@code provider_credentials} and from nowhere else, and every failure names the missing field
 * rather than a value. The second is not cosmetic — this class handles nothing but secrets, so an
 * exception message that interpolated what it found would write a partner token into the logs.
 */
class FlixBusCredentialsTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final Provider FLIXBUS = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS,
            ProviderCategory.BUS, "FlixBus", true, Set.of(), "https://partner.test", 5_000, 2, NOW, NOW);

    private static final String TOKEN = "partner-token-abc";
    private static final String EMAIL = "partner@roadscanner.com";
    private static final String PASSWORD = "s3cret-password";

    private InMemoryProviderCredentialsRepository repository;
    private FlixBusCredentials credentials;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProviderCredentialsRepository();
        credentials = new FlixBusCredentials(repository);
    }

    private void store(String email, String password, String token) {
        repository.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), FLIXBUS.id(),
                email, password, token, NOW));
    }

    @Test
    void resolvesThePartnerTokenFromTheStore() {
        store(EMAIL, PASSWORD, TOKEN);

        assertThat(credentials.partnerToken(FLIXBUS)).isEqualTo(TOKEN);
    }

    @Test
    void resolvesThePartnerLoginFromTheStore() {
        store(EMAIL, PASSWORD, TOKEN);

        FlixBusCredentials.PartnerLogin login = credentials.partnerLogin(FLIXBUS);

        assertThat(login.email()).isEqualTo(EMAIL);
        assertThat(login.password()).isEqualTo(PASSWORD);
    }

    @Test
    void reflectsARotatedSecretWithoutARestart() {
        // The reason credentials are not read from configuration at all: an operator rotating a
        // secret through the admin API must take effect on the very next call.
        store(EMAIL, PASSWORD, TOKEN);
        repository.findByProvider(FLIXBUS.id()).orElseThrow().rotate(EMAIL, PASSWORD, "rotated-token", NOW);

        assertThat(credentials.partnerToken(FLIXBUS)).isEqualTo("rotated-token");
    }

    @Test
    void failsWithAnActionableMessageWhenNoCredentialsAreStored() {
        assertThatThrownBy(() -> credentials.partnerToken(FLIXBUS))
                .isInstanceOf(ProviderAuthenticationException.class)
                .hasMessageContaining("No credentials are stored")
                // The message must say what to do about it, not merely that something is absent.
                .hasMessageContaining("/credentials");
    }

    @Test
    void failsWhenTheStoredRowCarriesNoToken() {
        store(EMAIL, PASSWORD, null);

        assertThatThrownBy(() -> credentials.partnerToken(FLIXBUS))
                .isInstanceOf(ProviderAuthenticationException.class)
                .hasMessageContaining("no partner token");
    }

    @Test
    void failsWhenTheStoredRowCarriesNoLoginPair() {
        store(null, null, TOKEN);

        assertThatThrownBy(() -> credentials.partnerLogin(FLIXBUS))
                .isInstanceOf(ProviderAuthenticationException.class)
                .hasMessageContaining("no partner email");
    }

    @Test
    void aFailureNeverCarriesTheSecretItWasLookingFor() {
        // A credentials resolver that interpolates what it found is how a partner token reaches a
        // log file. Asserted against every rendering an operator or handler could reach.
        store(EMAIL, PASSWORD, null);

        assertThatThrownBy(() -> credentials.partnerToken(FLIXBUS))
                .satisfies(thrown -> {
                    assertThat(thrown.getMessage()).doesNotContain(PASSWORD).doesNotContain(EMAIL);
                    assertThat(thrown.toString()).doesNotContain(PASSWORD).doesNotContain(EMAIL);
                });
    }

    @Test
    void aPartnerLoginNeverRendersItsSecrets() {
        // Records print every component by default, so an unguarded PartnerLogin in a log line or
        // a debugger frame would show the password in clear text.
        store(EMAIL, PASSWORD, TOKEN);

        String rendered = credentials.partnerLogin(FLIXBUS).toString();

        assertThat(rendered).doesNotContain(PASSWORD).doesNotContain(EMAIL);
        assertThat(rendered).isEqualTo("PartnerLogin[email=set, password=set]");
    }

    @Test
    void looksUpCredentialsByProviderIdentityRatherThanByType() {
        // Two rows of the same type must not be interchangeable: resolving by type would let a
        // second FlixBus registration authenticate with the first one's secret.
        store(EMAIL, PASSWORD, TOKEN);

        Provider otherRegistration = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS,
                ProviderCategory.BUS, "FlixBus EU", true, Set.of(), "https://eu.test", 5_000, 2, NOW, NOW);

        assertThatThrownBy(() -> credentials.partnerToken(otherRegistration))
                .isInstanceOf(ProviderAuthenticationException.class);
    }
}
