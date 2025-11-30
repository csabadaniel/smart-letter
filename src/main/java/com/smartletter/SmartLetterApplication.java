package com.smartletter;

import com.smartletter.settings.config.DeliveryConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DeliveryConfigurationProperties.class)
public class SmartLetterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartLetterApplication.class, args);
	}

}
