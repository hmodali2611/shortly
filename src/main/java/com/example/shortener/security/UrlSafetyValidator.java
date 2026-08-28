package com.example.shortener.security;

import com.example.shortener.common.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyValidator {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
	private final HostResolver hostResolver;

	public UrlSafetyValidator() {
		this(InetAddress::getAllByName);
	}

	UrlSafetyValidator(HostResolver hostResolver) {
		this.hostResolver = hostResolver;
	}

	public void validate(String targetUrl) {
		URI uri;
		try {
			uri = new URI(targetUrl);
		} catch (URISyntaxException exception) {
			throw rejected("Target URL is malformed");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost();
		if (!ALLOWED_SCHEMES.contains(scheme) || host == null || uri.getUserInfo() != null) {
			throw rejected("Target must be an HTTP(S) URL without embedded credentials");
		}
		String normalizedHost = host.toLowerCase(Locale.ROOT);
		if ((!normalizedHost.contains(".") && !normalizedHost.contains(":")) || normalizedHost.endsWith(".local")
				|| normalizedHost.endsWith(".internal")) {
			throw rejected("Internal and unqualified hosts are not allowed");
		}
		try {
			for (InetAddress address : hostResolver.resolve(host)) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
					throw rejected("Target resolves to a non-public address");
				}
			}
		} catch (UnknownHostException exception) {
			throw rejected("Target host cannot be resolved");
		}
	}

	private ApiException rejected(String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "unsafe-target", message);
	}

	@FunctionalInterface
	interface HostResolver {
		InetAddress[] resolve(String host) throws UnknownHostException;
	}
}