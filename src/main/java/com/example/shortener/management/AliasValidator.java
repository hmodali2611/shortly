package com.example.shortener.management;

import com.example.shortener.common.ApiException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AliasValidator {

	public static final int MAX_LENGTH = 32;
	private static final Pattern FORMAT = Pattern.compile("^[a-zA-Z0-9_-]{3," + MAX_LENGTH + "}$");
	private static final Set<String> RESERVED = Set.of("api", "admin", "health", "healthz", "readyz", "actuator",
			"metrics", "login", "static", "favicon.ico");
	private static final Set<String> BLOCKED_TERMS = Set.of("adminroot", "malware", "phishing");

	public void validate(String alias) {
		if (!FORMAT.matcher(alias).matches()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-alias", "Alias must be 3-32 URL-safe characters");
		}
		String normalized = alias.toLowerCase(Locale.ROOT);
		if (RESERVED.contains(normalized) || BLOCKED_TERMS.stream().anyMatch(normalized::contains)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-alias", "Alias is reserved or blocked");
		}
	}
}