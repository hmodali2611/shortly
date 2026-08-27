package com.example.shortener.management;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.example.shortener.security.ApiKeyAuthenticator;
import com.example.shortener.security.RateLimiter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

	private final LinkService linkService;
	private final ApiKeyAuthenticator authenticator;
	private final RateLimiter rateLimiter;

	public LinkController(LinkService linkService, ApiKeyAuthenticator authenticator, RateLimiter rateLimiter) {
		this.linkService = linkService;
		this.authenticator = authenticator;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping
	ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request,
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		String ownerKeyId = authenticator.authenticate(authorization);
		rateLimiter.checkCreate(ownerKeyId);
		LinkResponse response = linkService.create(request, ownerKeyId);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{shortCode}")
	LinkResponse metadata(@PathVariable String shortCode,
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		return linkService.metadata(shortCode, authenticator.authenticate(authorization));
	}

	@DeleteMapping("/{shortCode}")
	ResponseEntity<Void> delete(@PathVariable String shortCode,
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		linkService.delete(shortCode, authenticator.authenticate(authorization));
		return ResponseEntity.noContent().build();
	}
}