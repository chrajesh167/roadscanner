package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link CityRepositoryAdapter} against a real Postgres (Testcontainers), verifying
 * the V2 seed geography — cities are administratively managed, never written by this service's
 * own use cases (docs/services/inventory-service/domain-model.md), so Flyway's seed rows are the
 * only source of test data here. */
@DataJpaTest
@Import({TestcontainersConfiguration.class, CityRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CityRepositoryAdapterTest {

    private static final CityId MUMBAI_ID = new CityId(UUID.fromString("11111111-1111-1111-1111-111111111101"));

    @Autowired
    private CityRepositoryAdapter adapter;

    @Test
    void findByIdReturnsSeedCity() {
        City mumbai = adapter.findById(MUMBAI_ID).orElseThrow();

        assertThat(mumbai.name()).isEqualTo("Mumbai");
        assertThat(mumbai.state()).isEqualTo("Maharashtra");
        assertThat(mumbai.country()).isEqualTo("India");
    }

    @Test
    void findByIdIsEmptyForAnUnknownId() {
        assertThat(adapter.findById(CityId.generate())).isEmpty();
    }

    @Test
    void searchByPrefixMatchesSeedCitiesCaseInsensitively() {
        assertThat(adapter.searchByPrefix("mum", 10)).extracting(City::name).contains("Mumbai");
        assertThat(adapter.searchByPrefix("Che", 10)).extracting(City::name).contains("Chennai");
        assertThat(adapter.searchByPrefix("zzz", 10)).isEmpty();
    }

    @Test
    void searchByPrefixRespectsLimit() {
        assertThat(adapter.searchByPrefix("", 2)).hasSize(2);
    }
}
