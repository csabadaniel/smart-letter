package com.smartletter.settings.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DeliveryConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsExplicitPropertyValues() {
        contextRunner
                .withPropertyValues(
                        "delivery-config.collection-path=appSettings/configuration/delivery",
                        "delivery-config.cache-ttl-seconds=42",
                        "delivery-config.rate-limit.writes-per-hour=10",
                        "delivery-config.rate-limit.reads-per-hour=20")
                .run(context -> {
                    DeliveryConfigurationProperties properties = context
                            .getBean(DeliveryConfigurationProperties.class);

                    assertThat(properties.getCollectionPath()).isEqualTo("appSettings/configuration/delivery");
                    assertThat(properties.getCacheTtlSeconds()).isEqualTo(42);
                    assertThat(properties.getRateLimit().getWritesPerHour()).isEqualTo(10);
                    assertThat(properties.getRateLimit().getReadsPerHour()).isEqualTo(20);
                });
    }

    @Test
    void usesDocumentedDefaultsWhenNotOverridden() {
        contextRunner
                .withPropertyValues("delivery-config.collection-path=appSettings/configuration/delivery")
                .run(context -> {
            DeliveryConfigurationProperties properties = context.getBean(DeliveryConfigurationProperties.class);

            assertThat(properties.getCollectionPath()).isEqualTo("appSettings/configuration/delivery");
            assertThat(properties.getCacheTtlSeconds()).isEqualTo(60);
            assertThat(properties.getRateLimit().getWritesPerHour()).isEqualTo(30);
            assertThat(properties.getRateLimit().getReadsPerHour()).isEqualTo(120);
        });
    }

    @EnableConfigurationProperties(DeliveryConfigurationProperties.class)
    private static class TestConfig {
    }
}
