package com.smartletter;

import com.smartletter.settings.config.DeliveryConfigurationProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(DeliveryConfigurationProperties.class)
public class SmartLetterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartLetterApplication.class, args);
	}

	@Bean
	Clock utcClock() {
		return Clock.systemUTC();
	}

}
