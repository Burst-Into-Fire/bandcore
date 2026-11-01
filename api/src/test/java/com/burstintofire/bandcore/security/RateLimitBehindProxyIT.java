package com.burstintofire.bandcore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.TestFixtures;

/**
 * The test client connects from the loopback address, configured here as the trusted proxy, so
 * the client address comes from {@code X-Forwarded-For}.
 */
@TestPropertySource(properties = "bandcore.platform.trusted-proxies=127.0.0.1,0:0:0:0:0:0:0:1")
class RateLimitBehindProxyIT extends IntegrationTest {

	@BeforeEach
	void ana() {
		UUID alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "MEMBER");
	}

	@Test
	void theAccountLimitStillAppliesWhenTheAddressChanges() {
		for (int i = 0; i < 5; i++) {
			assertThat(loginFrom("203.0.113." + i, "wrong password!!").getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		RateLimitIT.assertTooManyRequests(loginFrom("203.0.113.99", TestFixtures.PASSWORD), Duration.ofMinutes(15));
	}

	@Test
	void eachClientAddressHasItsOwnLimit() {
		for (int i = 0; i < 20; i++) {
			api.central()
				.postJson("/api/auth/login", RateLimitIT.login("user" + i + "@example.com", "wrong password!!"),
						headers -> headers.set("X-Forwarded-For", "203.0.113.1"));
		}

		RateLimitIT.assertTooManyRequests(loginFrom("203.0.113.1", TestFixtures.PASSWORD), Duration.ofMinutes(5));
		assertThat(loginFrom("203.0.113.2", TestFixtures.PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void entriesTheClientAddedToTheLeftAreIgnored() {
		for (int i = 0; i < 20; i++) {
			String spoofed = "198.51.100." + i + ", 203.0.113.1";
			api.central()
				.postJson("/api/auth/login", RateLimitIT.login("user" + i + "@example.com", "wrong password!!"),
						headers -> headers.set("X-Forwarded-For", spoofed));
		}

		RateLimitIT.assertTooManyRequests(loginFrom("198.51.100.250, 203.0.113.1", TestFixtures.PASSWORD),
				Duration.ofMinutes(5));
	}

	private ResponseEntity<String> loginFrom(String forwardedFor, String password) {
		return api.central()
			.postJson("/api/auth/login", RateLimitIT.login("ana@example.com", password),
					headers -> headers.set("X-Forwarded-For", forwardedFor));
	}

}
