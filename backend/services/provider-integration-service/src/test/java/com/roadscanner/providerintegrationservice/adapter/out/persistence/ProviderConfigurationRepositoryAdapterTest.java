package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link ProviderConfigurationRepositoryAdapter} against real Postgres, including the
 * {@code V5__seed_provider_configurations.sql} seed data every environment ships with. */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ProviderConfigurationRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProviderConfigurationRepositoryAdapterTest {

    @Autowired
    private ProviderConfigurationRepositoryAdapter adapter;

    @Test
    void mockIsSeededEnabledWithEveryCapability() {
        Optional<Provider> mock = adapter.findByType(ProviderType.MOCK);

        assertThat(mock).isPresent();
        assertThat(mock.get().enabled()).isTrue();
        assertThat(mock.get().capabilities()).containsExactlyInAnyOrder(ProviderCapability.values());
    }

    @Test
    void flixbusIsSeededDisabledPendingRealCredentials() {
        Optional<Provider> flixbus = adapter.findByType(ProviderType.FLIXBUS);

        assertThat(flixbus).isPresent();
        assertThat(flixbus.get().enabled()).isFalse();
    }

    @Test
    void findAllEnabledReturnsOnlyMock() {
        List<Provider> enabled = adapter.findAllEnabled();

        assertThat(enabled).extracting(Provider::type).containsExactly(ProviderType.MOCK);
    }

    @Test
    void returnsEmptyForAnUnconfiguredProviderType() {
        assertThat(adapter.findByType(new ProviderType("REDBUS"))).isEmpty();
    }
}
