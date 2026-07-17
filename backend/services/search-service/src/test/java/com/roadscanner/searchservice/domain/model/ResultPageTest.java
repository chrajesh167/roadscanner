package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultPageTest {

    @Test
    void ofComputesTotalPages() {
        ResultPage<String> page = ResultPage.of(List.of("a", "b"), 0, 20, 45);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void withContentPreservesPagingMetadata() {
        ResultPage<String> page = ResultPage.of(List.of("a"), 1, 10, 25);
        ResultPage<Integer> remapped = page.withContent(List.of(1));

        assertThat(remapped.content()).containsExactly(1);
        assertThat(remapped.page()).isEqualTo(1);
        assertThat(remapped.size()).isEqualTo(10);
        assertThat(remapped.totalElements()).isEqualTo(25);
        assertThat(remapped.totalPages()).isEqualTo(page.totalPages());
    }

    @Test
    void rejectsInvalidPagingValues() {
        assertThatThrownBy(() -> new ResultPage<>(List.of(), -1, 10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultPage<>(List.of(), 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultPage<>(List.of(), 0, 10, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
