package com.roadscanner.providerintegrationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Execution settings that are platform-wide rather than per-provider.
 *
 * <p>The split matters. Timeout and retry count live on the provider row, because they describe
 * <em>that provider's</em> behaviour and an operator must be able to change one without touching
 * the others. Backoff shape and pool size live here, because they describe how <em>this service</em>
 * behaves under load and are meaningless to tune per provider.
 *
 * @param backoffInitial    delay after the first failure, before jitter
 * @param backoffMultiplier growth factor per subsequent attempt
 * @param backoffMax        cap on any single delay, so a high retry count cannot hold a request
 *                          thread for minutes
 * @param backoffJitter     randomise delays; on everywhere except tests asserting exact values
 * @param poolSize          workers available for provider calls. Bounded on purpose — a timed-out
 *                          call keeps its worker until its socket timeout fires, so an unbounded
 *                          pool would let a slow provider spawn threads without limit
 * @param shutdownGrace     how long to let in-flight calls finish on shutdown
 */
@ConfigurationProperties(prefix = "roadscanner.provider.execution")
public record ProviderExecutionProperties(Duration backoffInitial, double backoffMultiplier, Duration backoffMax,
                                          boolean backoffJitter, int poolSize, Duration shutdownGrace) {

    public ProviderExecutionProperties {
        if (poolSize < 1) {
            throw new IllegalArgumentException("roadscanner.provider.execution.pool-size must be at least 1");
        }
    }
}
