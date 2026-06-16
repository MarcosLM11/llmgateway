package com.marcos.llmgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmgatewayApplication {

	static void main(String[] args) {
		SpringApplication.run(LlmgatewayApplication.class, args);
	}

}
