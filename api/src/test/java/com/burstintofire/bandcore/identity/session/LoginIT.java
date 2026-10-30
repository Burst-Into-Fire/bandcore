package com.burstintofire.bandcore.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.util.Base64;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.Cookies;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.TestFixtures;

class LoginIT extends IntegrationTest {

	@MockitoSpyBean
	PasswordEncoder passwordEncoder;

	UUID alpha;

	UUID beta;

	@BeforeEach
	void bandsAndUsers() {
		alpha = fixtures.band("alpha");
		beta = fixtures.band("beta");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.user("unverified@example.com", TestFixtures.PASSWORD, false);
	}

	@Test
	void loginOnABandHostReturnsABandScopedTokenAndTheRefreshCookie() {
		ResponseEntity<String> response = login(api.forBand("alpha"), "ana@example.com", TestFixtures.PASSWORD);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(response.getBody(), "$.tokenType")).isEqualTo("Bearer");
		assertThat(JsonPath.<Integer>read(response.getBody(), "$.expiresIn")).isEqualTo(900);
		String claims = claims(JsonPath.read(response.getBody(), "$.accessToken"));
		assertThat(claims).contains("\"band_id\":\"" + alpha + "\"")
			.contains("\"role\":\"ADMIN\"")
			.contains("\"aud\":\"alpha.localhost\"")
			.contains("\"iss\":\"bandcore\"");

		String cookie = Cookies.refreshSetCookie(response);
		assertThat(cookie).startsWith("__Host-bandcore_rt=")
			.contains("; Path=/")
			.contains("; Max-Age=1209600")
			.contains("; Secure")
			.contains("; HttpOnly")
			.contains("; SameSite=Strict")
			.doesNotContainIgnoringCase("Domain=");
		assertThat(response.getBody()).doesNotContain(Cookies.refreshToken(response));
	}

	@Test
	void loginOnTheCentralHostGivesASessionWithoutBand() {
		ResponseEntity<String> response = login(api.central(), "ANA@example.com", TestFixtures.PASSWORD);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(claims(JsonPath.read(response.getBody(), "$.accessToken"))).doesNotContain("band_id")
			.contains("\"aud\":\"app.localhost\"");
	}

	@Test
	void memberOfOneBandCannotLogInOnAnotherBandsHost() {
		ResponseEntity<String> otherBand = login(api.forBand("beta"), "ana@example.com", TestFixtures.PASSWORD);
		ResponseEntity<String> wrongPassword = login(api.forBand("alpha"), "ana@example.com", "wrong password!!");

		assertInvalidCredentials(otherBand);
		assertThat(body(otherBand)).isEqualTo(body(wrongPassword));
	}

	@Test
	void unverifiedAccountCannotLogInOnABandHost() {
		UUID unverified = fixtures.user("pending@example.com", TestFixtures.PASSWORD, false);
		fixtures.membership(alpha, unverified, "MEMBER");

		assertInvalidCredentials(login(api.forBand("alpha"), "pending@example.com", TestFixtures.PASSWORD));
		assertThat(login(api.central(), "pending@example.com", TestFixtures.PASSWORD).getStatusCode())
			.isEqualTo(HttpStatus.OK);
	}

	@Test
	void unknownEmailAndWrongPasswordLookTheSame() {
		ResponseEntity<String> unknown = login(api.forBand("alpha"), "nobody@example.com", TestFixtures.PASSWORD);
		ResponseEntity<String> wrong = login(api.forBand("alpha"), "ana@example.com", "wrong password!!");

		assertInvalidCredentials(unknown);
		assertInvalidCredentials(wrong);
		assertThat(body(unknown)).isEqualTo(body(wrong));
		assertThat(Cookies.refreshSetCookie(unknown)).isNull();
	}

	@Test
	void unknownEmailStillRunsThePasswordEncoder() {
		clearInvocations(passwordEncoder);

		login(api.central(), "nobody@example.com", "some password here");

		verify(passwordEncoder).matches(eq("some password here"), anyString());
	}

	@Test
	void loginWithoutTheCsrfHeaderIsForbidden() {
		ResponseEntity<String> response = api.forBand("alpha")
			.withoutCsrfHeader()
			.postJson("/api/auth/login", credentials("ana@example.com", TestFixtures.PASSWORD));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(Cookies.refreshSetCookie(response)).isNull();
	}

	@Test
	void meDescribesTheUserAndTheirRoleInTheBand() {
		String bandToken = fixtures.login(api.forBand("alpha"), "ana@example.com");
		String centralToken = fixtures.login(api.central(), "ana@example.com");

		ResponseEntity<String> inBand = api.forBand("alpha").get("/api/me", headers -> headers.setBearerAuth(bandToken));
		ResponseEntity<String> central = api.central().get("/api/me", headers -> headers.setBearerAuth(centralToken));

		assertThat(inBand.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(inBand.getBody(), "$.email")).isEqualTo("ana@example.com");
		assertThat(JsonPath.<String>read(inBand.getBody(), "$.band.slug")).isEqualTo("alpha");
		assertThat(JsonPath.<String>read(inBand.getBody(), "$.band.role")).isEqualTo("ADMIN");
		assertThat(JsonPath.<Object>read(central.getBody(), "$.band")).isNull();
		assertThat(JsonPath.<Boolean>read(central.getBody(), "$.emailVerified")).isTrue();
	}

	@Test
	void meWithoutTokenIsUnauthorized() {
		ResponseEntity<String> response = api.forBand("alpha").get("/api/me");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("UNAUTHENTICATED");
	}

	private ResponseEntity<String> login(ApiClient client, String email, String password) {
		return client.postJson("/api/auth/login", credentials(email, password));
	}

	private static String credentials(String email, String password) {
		return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
	}

	private static void assertInvalidCredentials(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("INVALID_CREDENTIALS");
	}

	/** Problem body without the request id, which differs per request. */
	private static String body(ResponseEntity<String> response) {
		return response.getBody().replaceAll("\"requestId\":\"[^\"]*\"", "");
	}

	static String claims(String token) {
		return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
	}

}
