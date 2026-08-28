package com.example.shortener.management;

import com.example.shortener.analytics.IpHasher;
import com.example.shortener.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

	private final LinkService linkService;
	private final RateLimiter rateLimiter;
	private final IpHasher ipHasher;

	public LinkController(LinkService linkService, RateLimiter rateLimiter, IpHasher ipHasher) {
		this.linkService = linkService;
		this.rateLimiter = rateLimiter;
		this.ipHasher = ipHasher;
	}

	@PostMapping
	ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request, HttpServletRequest httpRequest) {
		rateLimiter.checkCreate(ipHasher.hash(httpRequest.getRemoteAddr()));
		LinkResponse response = linkService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{shortCode}")
	LinkResponse metadata(@PathVariable String shortCode) {
		return linkService.metadata(shortCode);
	}

	@DeleteMapping("/{shortCode}")
	ResponseEntity<Void> delete(@PathVariable String shortCode) {
		linkService.delete(shortCode);
		return ResponseEntity.noContent().build();
	}
}