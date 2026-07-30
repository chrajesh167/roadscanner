package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.execution.BackoffStrategy;
import com.roadscanner.providerintegrationservice.execution.ExponentialBackoffStrategy;
import com.roadscanner.providerintegrationservice.execution.ProviderExecutionExecutor;
import com.roadscanner.providerintegrationservice.execution.ProviderMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wires the shared provider execution layer.
 *
 * <p>The pool is deliberately fixed and its queue deliberately shallow. A deep queue would turn a
 * slow provider into slowly-growing latency for every caller — requests would sit in the queue,
 * still counting against their own timeouts, and the system would look healthy while getting
 * steadily worse. A shallow queue makes saturation fail fast and visibly, which
 * {@code ProviderExecutionExecutor} surfaces as an unavailable provider.
 */
@Configuration
@EnableConfigurationProperties(ProviderExecutionProperties.class)
public class ProviderExecutionConfig {

    @Bean
    public ProviderMetrics providerMetrics(MeterRegistry meterRegistry) {
        return new ProviderMetrics(meterRegistry);
    }

    @Bean
    public BackoffStrategy providerBackoffStrategy(ProviderExecutionProperties properties) {
        return new ExponentialBackoffStrategy(properties.backoffInitial(), properties.backoffMultiplier(),
                properties.backoffMax(), properties.backoffJitter());
    }

    /**
     * Threads are named so a stack dump during an incident says which pool is saturated, and are
     * daemons so a hung provider call cannot keep the JVM alive after shutdown.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService providerCallExecutor(ProviderExecutionProperties properties) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                properties.poolSize(), properties.poolSize(),
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(properties.poolSize()),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("provider-call-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(false);
        return pool;
    }

    @Bean
    public ProviderExecutionExecutor providerExecutionExecutor(ExecutorService providerCallExecutor,
                                                               ProviderMetrics providerMetrics) {
        return new ProviderExecutionExecutor(providerCallExecutor, providerMetrics);
    }
}
