package com.smartletter.settings.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed binding for the delivery configuration section in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "delivery-config")
@Validated
public class DeliveryConfigurationProperties {

    @NotBlank
    private String collectionPath;

    @Min(1)
    private long cacheTtlSeconds = 60L;

    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {

        @Positive
        private int writesPerHour = 30;

        @Positive
        private int readsPerHour = 120;
    }
}
