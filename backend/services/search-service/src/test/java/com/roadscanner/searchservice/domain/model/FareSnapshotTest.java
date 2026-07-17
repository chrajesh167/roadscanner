package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareSnapshotTest {

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new FareSnapshot(new BigDecimal("-1"), Currency.getInstance("INR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> new FareSnapshot(null, Currency.getInstance("INR")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FareSnapshot(BigDecimal.TEN, null))
                .isInstanceOf(NullPointerException.class);
    }
}
