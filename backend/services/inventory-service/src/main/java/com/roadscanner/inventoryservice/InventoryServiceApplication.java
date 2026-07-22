package com.roadscanner.inventoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} backs {@code config.CatalogSyncScheduler} — catalog synchronization
 * against every configured provider. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
