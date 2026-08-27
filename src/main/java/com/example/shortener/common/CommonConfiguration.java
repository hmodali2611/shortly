package com.example.shortener.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}