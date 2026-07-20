package com.roadscanner.providerintegrationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} backs {@code ProviderHealthMonitorScheduler} and
 * {@code SessionExpirySweeperScheduler} (see {@code config}) — the only two scheduled jobs this
 * service runs. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ProviderIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderIntegrationServiceApplication.class, args);
    }
}
