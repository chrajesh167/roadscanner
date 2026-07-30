package com.roadscanner.searchservice.location.domain.port.out;

/**
 * Guards the metered place-autocomplete provider.
 *
 * <p>The cache already removes repeat lookups; this bounds the ones that get through. Autocomplete
 * fires on every keystroke against a paid API, so without a ceiling a single scripted client — or
 * one enthusiastic user holding a key down — can spend real money and exhaust the quota that every
 * other traveller depends on.
 *
 * <p>A port rather than a concrete limiter so the in-process implementation can be replaced by a
 * Redis-backed one when a per-instance ceiling stops being good enough, without touching the use
 * case.
 */
public interface PlaceAutocompleteRateLimiter {

    /**
     * Consumes one permit if available.
     *
     * @return true when the call may proceed; false when the limit is exhausted. Deliberately does
     *         not block — an autocomplete request that waits its turn is worse than one that is
     *         refused quickly, because the user has typed another character by then anyway.
     */
    boolean tryAcquire();
}
