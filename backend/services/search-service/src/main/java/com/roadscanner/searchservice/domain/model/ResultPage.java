package com.roadscanner.searchservice.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A generic, framework-free paged result — reused for both the repository port's raw
 * {@code SearchableTrip} pages and the application layer's availability-overlaid
 * {@code TripSearchResult} pages, so the paging shape (page/size/totalElements/totalPages)
 * exists exactly once rather than duplicated across two near-identical record types. Deliberately
 * named {@code ResultPage}, not {@code Page}, to avoid ambiguity with Spring Data's {@code Page}
 * type used inside the persistence adapter — this type must never leak into that comparison,
 * since {@code domain/port/out} depends on nothing outside the domain layer (mirroring
 * {@code auth-service}'s dependency-direction discipline).
 */
public record ResultPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public ResultPage {
        Objects.requireNonNull(content, "content must not be null");
        content = List.copyOf(content);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must not be negative");
        }
    }

    public static <T> ResultPage<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new ResultPage<>(content, page, size, totalElements, totalPages);
    }

    /** Re-wraps this page's content under the same paging metadata — used to attach the
     * availability overlay without recomputing page/size/totalElements/totalPages. */
    public <R> ResultPage<R> withContent(List<R> newContent) {
        return new ResultPage<>(newContent, page, size, totalElements, totalPages);
    }
}
