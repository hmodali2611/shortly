package com.example.shortener.management;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateLinkRequest(@NotBlank @Size(max = 2048) String targetUrl,
		@Size(max = AliasValidator.MAX_LENGTH) String customAlias, Instant expiresAt) {
}