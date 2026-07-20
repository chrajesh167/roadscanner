package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.application.usecase.health.ProviderHealthMonitor;
import com.roadscanner.providerintegrationservice.application.usecase.session.SessionExpirySweeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The only two scheduled jobs this service runs — thin trigger beans, all actual logic lives in
 * {@link ProviderHealthMonitor}/{@link SessionExpirySweeper} (framework-free, independently
 * testable). Intervals come from {@link ProviderProperties} rather than being hardcoded, so they
 * can be tuned per environment without a code change.
 */
@Component
public class ProviderMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderMaintenanceScheduler.class);

    private final ProviderHealthMonitor healthMonitor;
    private final SessionExpirySweeper sessionExpirySweeper;

    public ProviderMaintenanceScheduler(ProviderHealthMonitor healthMonitor, SessionExpirySweeper sessionExpirySweeper) {
        this.healthMonitor = healthMonitor;
        this.sessionExpirySweeper = sessionExpirySweeper;
    }

    @Scheduled(fixedDelayString = "${roadscanner.provider.health-check-interval}")
    public void checkProviderHealth() {
        try {
            healthMonitor.checkAllEnabledProviders();
        } catch (RuntimeException e) {
            log.error("Provider health check sweep failed unexpectedly", e);
        }
    }

    @Scheduled(fixedDelayString = "${roadscanner.provider.session-expiry-sweep-interval}")
    public void sweepExpiredSessions() {
        try {
            int swept = sessionExpirySweeper.sweepExpiredSessions();
            if (swept > 0) {
                log.info("Session expiry sweep marked {} session(s) EXPIRED", swept);
            }
        } catch (RuntimeException e) {
            log.error("Session expiry sweep failed unexpectedly", e);
        }
    }
}
