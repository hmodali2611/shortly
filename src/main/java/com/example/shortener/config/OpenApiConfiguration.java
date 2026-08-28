package com.example.shortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI shortenerOpenApi() {
		return new OpenAPI().info(new Info().title("URL Shortener API").version("v1")
				.description("Create, resolve, manage, and inspect short links."));
	}
}