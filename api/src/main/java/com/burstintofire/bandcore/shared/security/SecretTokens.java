package com.burstintofire.bandcore.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Single-use secrets sent to users (verification links, refresh tokens): 256 random bits,
 * URL-safe. Only their SHA-256 hash is stored, so a database leak does not reveal live tokens.
 */
public final class SecretTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int BYTES = 32;

	private SecretTokens() {
	}

	public static String generate() {
		byte[] bytes = new byte[BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available", ex);
		}
	}

}
