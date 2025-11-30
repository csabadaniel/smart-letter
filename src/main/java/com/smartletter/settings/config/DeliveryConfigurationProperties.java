package com.smartletter.settings.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed binding for the delivery configuration section in application.yml.
 */
@ConfigurationProperties(prefix = "delivery-config")
@Validated
public class DeliveryConfigurationProperties {

    @NotBlank
    private String collectionPath;

    @Min(1)
    private long cacheTtlSeconds = 60L;

    private RateLimit rateLimit = new RateLimit();

    public String getCollectionPath() {
        return collectionPath;
    }

    public void setCollectionPath(String collectionPath) {
        this.collectionPath = collectionPath;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class RateLimit {

        @Positive
        private int writesPerHour = 30;

        @Positive
        private int readsPerHour = 120;

        public int getWritesPerHour() {
            return writesPerHour;
        }

        public void setWritesPerHour(int writesPerHour) {
            this.writesPerHour = writesPerHour;
        }

        public int getReadsPerHour() {
            return readsPerHour;
        }

        public void setReadsPerHour(int readsPerHour) {
            this.readsPerHour = readsPerHour;
        }
    }
}
