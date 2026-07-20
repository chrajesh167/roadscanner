package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifies which external provider a session/trip/adapter belongs to — deliberately a value
 * object wrapping a normalized code, not a Java {@code enum}. Onboarding a new provider (RedBus,
 * AbhiBus, KSRTC, IntrCity, ...) must never require recompiling this class or any {@code switch}
 * over it: it's a new {@code adapter/out/provider/<name>} package implementing
 * {@link com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient} plus a
 * {@code provider_configurations} row, resolved at runtime by
 * {@link com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry}. An
 * {@code enum} here would force exactly the code change this design exists to avoid.
 */
public record ProviderType(String code) {

    public static final ProviderType FLIXBUS = new ProviderType("FLIXBUS");
    public static final ProviderType MOCK = new ProviderType("MOCK");

    public ProviderType {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.strip().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return code;
    }
}
