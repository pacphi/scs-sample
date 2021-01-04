package io.pivotal.scs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StuffApplication {

	public static void main(String[] args) {
		SpringApplication.run(StuffApplication.class, args);
	}

}
