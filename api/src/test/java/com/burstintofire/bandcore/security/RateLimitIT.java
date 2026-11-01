package com.burstintofire.bandcore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestFixtures;

/**
 * Limits with their real values (application.yml). The test client is not a trusted proxy, so
 * every request comes from the same address, whatever {@code X-Forwarded-For} says.
 */
class RateLimitIT extends IntegrationTest {

	@BeforeEach
	void ana() {
		UUID alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "MEMBER");
	}

	@Test
	void loginsPerAddressAreLimitedAndForwardedForFromAnUntrustedClientIsIgnored() {
		for (int i = 0; i < 20; i++) {
			String spoofed = "198.51.100." + i;
			ResponseEntity<String> response = api.central()
				.postJson("/api/auth/login", login("user" + i + "@example.com", "wrong password!!"),
						headers -> headers.set("X-Forwarded-For", spoofed));
			assertThat(response.getStatusCode()).as("attempt %d", i + 1).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		ResponseEntity<String> blocked = api.central()
			.postJson("/api/auth/login", login("ana@example.com", TestFixtures.PASSWORD),
					headers -> headers.set("X-Forwarded-For", "198.51.100.200"));

		assertTooManyRequests(blocked, Duration.ofMinutes(5));
	}

	@Test
	void anAccountIsLockedTemporarilyAfterFiveFailuresEvenWithTheRightPassword() {
		for (int i = 0; i < 5; i++) {
			assertThat(login(api.central(), "wrong password!!").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		assertTooManyRequests(login(api.central(), TestFixtures.PASSWORD), Duration.ofMinutes(15));
		assertTooManyRequests(login(api.forBand("alpha"), TestFixtures.PASSWORD), Duration.ofMinutes(15));

		TestClockConfiguration.CLOCK.advance(Duration.ofMinutes(15));
		assertThat(login(api.central(), TestFixtures.PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void theLockLooksTheSameForAnUnknownEmail() {
		ResponseEntity<String> known = null;
		ResponseEntity<String> unknown = null;
		for (int i = 0; i < 6; i++) {
			known = login(api.central(), "wrong password!!");
			unknown = api.central().postJson("/api/auth/login", login("nobody@example.com", "wrong password!!"));
		}

		assertTooManyRequests(known, Duration.ofMinutes(15));
		assertTooManyRequests(unknown, Duration.ofMinutes(15));
		assertThat(withoutRequestId(unknown.getBody())).isEqualTo(withoutRequestId(known.getBody()));
	}

	@Test
	void successfulLoginsDoNotCountAsFailures() {
		for (int i = 0; i < 4; i++) {
			login(api.central(), "wrong password!!");
		}
		for (int i = 0; i < 5; i++) {
			assertThat(login(api.central(), TestFixtures.PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
		}

		assertThat(login(api.central(), "wrong password!!").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void signupIsLimitedPerEmail() {
		assertLimitedAfter(3, () -> api.central()
			.postJson("/api/auth/signup",
					"{\"email\": \"bia@example.com\", \"password\": \"a long and unique passphrase\", \"displayName\": \"Bia\"}"),
				HttpStatus.ACCEPTED, Duration.ofHours(1));
	}

	@Test
	void verificationResendIsLimitedPerEmail() {
		assertLimitedAfter(3,
				() -> api.central().postJson("/api/auth/resend-verification", "{\"email\": \"bia@example.com\"}"),
				HttpStatus.ACCEPTED, Duration.ofHours(1));
	}

	@Test
	void emailVerificationIsLimitedPerAddress() {
		assertLimitedAfter(30, () -> api.central().postJson("/api/auth/verify-email", "{\"token\": \"guess\"}"),
				HttpStatus.BAD_REQUEST, Duration.ofHours(1));
	}

	@Test
	void passwordResetRequestsAreLimitedPerEmail() {
		assertLimitedAfter(3,
				() -> api.central().postJson("/api/auth/password-reset", "{\"email\": \"ana@example.com\"}"),
				HttpStatus.ACCEPTED, Duration.ofHours(1));
	}

	@Test
	void passwordResetConfirmationsAreLimitedPerAddress() {
		assertLimitedAfter(20,
				() -> api.central()
					.postJson("/api/auth/password-reset/confirm",
							"{\"token\": \"guess\", \"password\": \"a brand new passphrase\"}"),
				HttpStatus.BAD_REQUEST, Duration.ofHours(1));
	}

	@Test
	void slugChecksAreLimitedPerAccount() {
		ApiClient central = api.central();
		String token = fixtures.login(central, "ana@example.com");

		assertLimitedAfter(60,
				() -> central.get("/api/bands/slug-availability?slug=free-slug",
						headers -> headers.setBearerAuth(token)),
				HttpStatus.OK, Duration.ofMinutes(1));
	}

	private void assertLimitedAfter(int allowed, Supplier<ResponseEntity<String>> call, HttpStatus usual,
			Duration window) {
		for (int i = 0; i < allowed; i++) {
			assertThat(call.get().getStatusCode()).as("attempt %d", i + 1).isEqualTo(usual);
		}
		assertTooManyRequests(call.get(), window);

		TestClockConfiguration.CLOCK.advance(window);
		assertThat(call.get().getStatusCode()).as("after the window").isEqualTo(usual);
	}

	static void assertTooManyRequests(ResponseEntity<String> response, Duration window) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("TOO_MANY_REQUESTS");
		long retryAfter = Long.parseLong(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
		assertThat(retryAfter).isBetween(1L, window.toSeconds());
	}

	private static String withoutRequestId(String body) {
		return body.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<id>");
	}

	static ResponseEntity<String> login(ApiClient client, String password) {
		return client.postJson("/api/auth/login", login("ana@example.com", password));
	}

	static String login(String email, String password) {
		return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
	}

}
