package com.example.shortener.config;

import java.util.Map;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsController {

	private final HealthEndpoint healthEndpoint;

	public OperationsController(HealthEndpoint healthEndpoint) {
		this.healthEndpoint = healthEndpoint;
	}

	@GetMapping("/healthz")
	Map<String, String> liveness() {
		return Map.of("status", "UP");
	}

	@GetMapping("/readyz")
	ResponseEntity<Map<String, String>> readiness() {
		Status status = healthEndpoint.health().getStatus();
		HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(httpStatus).body(Map.of("status", status.getCode()));
	}
}