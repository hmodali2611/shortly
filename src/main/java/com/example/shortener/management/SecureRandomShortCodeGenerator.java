package com.example.shortener.management;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomShortCodeGenerator implements ShortCodeGenerator {

	private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
			.toCharArray();
	private static final int CODE_LENGTH = 8;
	private final SecureRandom random = new SecureRandom();

	@Override
	public String generate() {
		char[] code = new char[CODE_LENGTH];
		for (int index = 0; index < code.length; index++) {
			code[index] = ALPHABET[random.nextInt(ALPHABET.length)];
		}
		return new String(code);
	}
}