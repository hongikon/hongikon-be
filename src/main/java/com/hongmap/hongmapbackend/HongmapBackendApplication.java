package com.hongmap.hongmapbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HongmapBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HongmapBackendApplication.class, args);
	}

}
