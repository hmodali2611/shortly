package com.example.shortener.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRandomShortCodeGeneratorTest {

	@Test
	void generatesBase62CodesUsingSecureRandom() throws Exception {
		SecureRandomShortCodeGenerator generator = new SecureRandomShortCodeGenerator();
		Set<String> generated = new HashSet<>();

		for (int count = 0; count < 1_000; count++) {
			String code = generator.generate();
			assertThat(code).matches("[A-Za-z0-9]{8}");
			generated.add(code);
		}

		Field random = SecureRandomShortCodeGenerator.class.getDeclaredField("random");
		random.setAccessible(true);
		assertThat(random.get(generator)).isInstanceOf(SecureRandom.class);
		assertThat(generated).hasSize(1_000);
	}
}