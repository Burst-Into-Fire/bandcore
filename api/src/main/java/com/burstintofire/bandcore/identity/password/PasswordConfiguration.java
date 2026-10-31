package com.burstintofire.bandcore.identity.password;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {

	/** Argon2id parameters: OWASP's first recommendation (19 MiB, 2 iterations, 1 lane). */
	static final int SALT_LENGTH = 16;

	static final int HASH_LENGTH = 32;

	static final int PARALLELISM = 1;

	static final int MEMORY_KIB = 19 * 1024;

	static final int ITERATIONS = 2;

	/**
	 * Hashes are stored with an id prefix ({@code {argon2}...}), so stronger parameters or
	 * another algorithm can be introduced later while old hashes still verify.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB,
				ITERATIONS);
		return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
	}

}
