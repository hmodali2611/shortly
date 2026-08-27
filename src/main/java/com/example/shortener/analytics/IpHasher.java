package com.example.shortener.analytics;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IpHasher {

	private final byte[] secret;
	private final Clock clock;

	public IpHasher(@Value("${app.analytics.ip-hash-secret}") String secret, Clock clock) {
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.clock = clock;
	}

	public String hash(String ipAddress) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			String dailyValue = LocalDate.now(clock) + ":" + ipAddress;
			return HexFormat.of().formatHex(mac.doFinal(dailyValue.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to hash analytics address", exception);
		}
	}
}