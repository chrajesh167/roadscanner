package com.roadscanner.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteTest {

    @Test
    void rejectsIdenticalOriginAndDestinationCity() {
        CityId cityId = CityId.generate();

        assertThatThrownBy(() -> Route.create(RouteId.generate(), cityId, cityId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
