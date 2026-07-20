package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Objects;

/**
 * Performs a live probe against a provider, records the result, and — on a state transition —
 * publishes {@code ProviderUnavailable}/{@code ProviderRecovered}. Shared by two callers: the
 * REST health endpoint (on-demand) and {@code ProviderHealthMonitorScheduler} (every 30s) — one
 * implementation, so the "record + compare + publish" rule lives in exactly one place regardless
 * of who triggered the check.
 */
public interface CheckProviderHealth {

    Result check(Command command);

    record Command(ProviderType providerType) {
        public Command {
            Objects.requireNonNull(providerType, "providerType must not be null");
        }
    }

    record Result(ProviderHealth health) {
        public Result {
            Objects.requireNonNull(health, "health must not be null");
        }
    }
}
