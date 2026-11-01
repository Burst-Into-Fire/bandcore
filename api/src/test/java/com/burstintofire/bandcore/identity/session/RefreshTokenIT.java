package com.burstintofire.bandcore.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.Cookies;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestFixtures;

class RefreshTokenIT extends IntegrationTest {

	ApiClient alpha;

	@BeforeEach
	void memberOfAlpha() {
		UUID band = fixtures.band("alpha");
		fixtures.band("beta");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(band, ana, "MEMBER");
		alpha = api.forBand("alpha");
	}

	@Test
	void refreshRotatesTheTokenAndIssuesANewAccessToken() {
		String first = Cookies.refreshToken(login(alpha));

		ResponseEntity<String> refreshed = refresh(alpha, first);

		assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
		String second = Cookies.refreshToken(refreshed);
		assertThat(second).isNotEqualTo(first);
		assertThat(Cookies.refreshSetCookie(refreshed)).contains("; HttpOnly").contains("; SameSite=Strict");
		String accessToken = JsonPath.read(refreshed.getBody(), "$.accessToken");
		assertThat(alpha.get("/api/me", headers -> headers.setBearerAuth(accessToken)).getStatusCode())
			.isEqualTo(HttpStatus.OK);
	}

	@Test
	void reusingARotatedTokenRevokesTheWholeFamily() {
		String first = Cookies.refreshToken(login(alpha));
		String second = Cookies.refreshToken(refresh(alpha, first));

		ResponseEntity<String> reuse = refresh(alpha, first);

		assertUnauthorizedAndCleared(reuse);
		assertUnauthorizedAndCleared(refresh(alpha, second));
	}

	@Test
	void expiredRefreshTokenIsRejected() {
		String token = Cookies.refreshToken(login(alpha));
		OwnerSql.global("UPDATE bandcore.refresh_tokens SET expires_at = '%s'"
			.formatted(TestClockConfiguration.NOW.minusSeconds(1)));

		assertUnauthorizedAndCleared(refresh(alpha, token));
	}

	@Test
	void refreshNeverExtendsBeyondTheAbsoluteLifetime() {
		String token = Cookies.refreshToken(login(alpha));
		OwnerSql.global("UPDATE bandcore.refresh_tokens SET family_expires_at = '%s', expires_at = '%s'"
			.formatted(TestClockConfiguration.NOW.plusSeconds(3600), TestClockConfiguration.NOW.plusSeconds(3600)));

		ResponseEntity<String> refreshed = refresh(alpha, token);

		assertThat(Cookies.refreshSetCookie(refreshed)).contains("; Max-Age=3600");
	}

	@Test
	void refreshWithoutTheCsrfHeaderIsForbidden() {
		String token = Cookies.refreshToken(login(alpha));

		ResponseEntity<String> response = alpha.withoutCsrfHeader()
			.postJson("/api/auth/refresh", null, headers -> Cookies.send(headers, token));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(refresh(alpha, token).getStatusCode()).as("the token was not consumed").isEqualTo(HttpStatus.OK);
	}

	@Test
	void refreshWithoutCookieIsUnauthorized() {
		assertThat(alpha.postJson("/api/auth/refresh", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshTokenOfOneHostDoesNotWorkOnAnother() {
		String token = Cookies.refreshToken(login(alpha));

		assertUnauthorizedAndCleared(refresh(api.forBand("beta"), token));
		assertUnauthorizedAndCleared(refresh(api.central(), token));
	}

	@Test
	void logoutRevokesTheSessionAndClearsTheCookie() {
		String token = Cookies.refreshToken(login(alpha));

		ResponseEntity<String> logout = alpha.postJson("/api/auth/logout", null, headers -> Cookies.send(headers, token));

		assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(Cookies.refreshSetCookie(logout)).contains("Max-Age=0");
		assertUnauthorizedAndCleared(refresh(alpha, token));
	}

	@Test
	void logoutOnlyEndsThisBrowsersSession() {
		String laptop = Cookies.refreshToken(login(alpha));
		String phone = Cookies.refreshToken(login(alpha));

		alpha.postJson("/api/auth/logout", null, headers -> Cookies.send(headers, laptop));

		assertThat(refresh(alpha, phone).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void logoutAllEndsEverySessionOfTheUserOnEveryHost() {
		ResponseEntity<String> bandLogin = login(alpha);
		String bandSession = Cookies.refreshToken(bandLogin);
		String centralSession = Cookies.refreshToken(login(api.central()));
		String accessToken = JsonPath.read(bandLogin.getBody(), "$.accessToken");

		ResponseEntity<String> response = alpha.postJson("/api/auth/logout-all", null,
				headers -> headers.setBearerAuth(accessToken));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertUnauthorizedAndCleared(refresh(alpha, bandSession));
		assertUnauthorizedAndCleared(refresh(api.central(), centralSession));
	}

	@Test
	void logoutAllNeedsAnAccessToken() {
		assertThat(alpha.postJson("/api/auth/logout-all", null).getStatusCode()).isIn(HttpStatus.UNAUTHORIZED,
				HttpStatus.FORBIDDEN);
	}

	private ResponseEntity<String> login(ApiClient client) {
		ResponseEntity<String> response = client.postJson("/api/auth/login",
				"{\"email\": \"ana@example.com\", \"password\": \"%s\"}".formatted(TestFixtures.PASSWORD));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response;
	}

	private static ResponseEntity<String> refresh(ApiClient client, String token) {
		return client.postJson("/api/auth/refresh", null, headers -> Cookies.send(headers, token));
	}

	private static void assertUnauthorizedAndCleared(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(Cookies.refreshSetCookie(response)).contains("Max-Age=0");
	}

}
