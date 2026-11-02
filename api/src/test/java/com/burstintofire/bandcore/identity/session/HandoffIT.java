package com.burstintofire.bandcore.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.Cookies;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestFixtures;

@ExtendWith(OutputCaptureExtension.class)
class HandoffIT extends IntegrationTest {

	UUID alpha;

	UUID anaInBeta;

	ApiClient alphaHost;

	ApiClient betaHost;

	@BeforeEach
	void anaInTwoBands() {
		alpha = fixtures.band("alpha", "Alpha");
		UUID beta = fixtures.band("beta", "Beta");
		fixtures.band("gamma", "Gamma");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		anaInBeta = fixtures.membership(beta, ana, "MEMBER");
		alphaHost = api.forBand("alpha");
		betaHost = api.forBand("beta");
	}

	@Test
	void fromTheCentralHostToABandWithoutThePassword(CapturedOutput output) {
		String url = issue(api.central(), "alpha");

		assertThat(url).startsWith("http://alpha.localhost:3000/auth/handoff#code=");
		ResponseEntity<String> exchanged = exchange(alphaHost, code(url));

		assertThat(exchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(Cookies.refreshSetCookie(exchanged)).startsWith("__Host-bandcore_rt=").contains("; HttpOnly");
		String token = JsonPath.read(exchanged.getBody(), "$.accessToken");
		String me = alphaHost.get("/api/me", headers -> headers.setBearerAuth(token)).getBody();
		assertThat(JsonPath.<String>read(me, "$.band.role")).isEqualTo("ADMIN");
		assertThat(OwnerSql.queryForString("SELECT count(*) FROM bandcore.security_events WHERE details->>'method' = 'handoff'"))
			.isEqualTo("1");
		assertThat(output.getOut()).doesNotContain(code(url));
	}

	@Test
	void fromOneBandToAnotherAndBackToTheCentralHost() {
		ResponseEntity<String> toBeta = exchange(betaHost, code(issue(alphaHost, "beta")));
		String betaToken = JsonPath.read(toBeta.getBody(), "$.accessToken");
		assertThat(JsonPath.<String>read(
				betaHost.get("/api/me", headers -> headers.setBearerAuth(betaToken)).getBody(), "$.band.role"))
			.isEqualTo("MEMBER");

		String toCentral = issue(betaHost, null);

		assertThat(toCentral).startsWith("http://app.localhost:3000/auth/handoff#code=");
		ResponseEntity<String> central = exchange(api.central(), code(toCentral));
		assertThat(central.getStatusCode()).isEqualTo(HttpStatus.OK);
		String centralToken = JsonPath.read(central.getBody(), "$.accessToken");
		assertThat(api.central().get("/api/me/bands", headers -> headers.setBearerAuth(centralToken)).getStatusCode())
			.isEqualTo(HttpStatus.OK);
	}

	@Test
	void aCodeWorksOnceOnlyOnItsHostAndForSixtySeconds() {
		String reused = code(issue(api.central(), "alpha"));
		exchange(alphaHost, reused);
		String otherBand = code(issue(api.central(), "alpha"));
		String central = code(issue(api.central(), "alpha"));
		String expired = code(issue(api.central(), "alpha"));
		TestClockConfiguration.CLOCK.advance(Duration.ofSeconds(61));

		assertUnauthorized(exchange(alphaHost, reused));
		assertUnauthorized(exchange(betaHost, otherBand));
		assertUnauthorized(exchange(api.central(), central));
		assertUnauthorized(exchange(alphaHost, expired));
		assertUnauthorized(exchange(alphaHost, "x".repeat(43)));
	}

	@Test
	void aCodeForBandAUsedOnBandBDoesNotWorkLaterOnBandAEither() {
		String code = code(issue(api.central(), "alpha"));

		assertUnauthorized(exchange(betaHost, code));
		assertThat(exchange(alphaHost, code).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void accessRemovedBetweenIssueAndExchangeEndsTheHandoff() {
		UUID beta = UUID.fromString(OwnerSql.queryForString("SELECT id::text FROM bandcore.bands WHERE slug = 'beta'"));
		fixtures.membership(beta, fixtures.verifiedUser("bia@example.com"), "ADMIN");
		String bia = fixtures.login(betaHost, "bia@example.com");
		String code = code(issue(api.central(), "beta"));

		betaHost.send(HttpMethod.DELETE, "/api/memberships/" + anaInBeta, null, headers -> headers.setBearerAuth(bia));

		assertUnauthorized(exchange(betaHost, code));
	}

	@Test
	void onlyBandsTheUserBelongsToCanBeTargeted() {
		String token = fixtures.login(api.central(), "ana@example.com");

		assertForbidden(api.central().postJson("/api/auth/handoff", "{\"slug\": \"gamma\"}", headers -> headers.setBearerAuth(token)));
		assertForbidden(api.central().postJson("/api/auth/handoff", "{\"slug\": \"does-not-exist\"}",
				headers -> headers.setBearerAuth(token)));
		assertThat(api.central().postJson("/api/auth/handoff", "{\"slug\": \"alpha\"}").getStatusCode())
			.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anUnverifiedAccountCannotOpenABand() {
		fixtures.membership(alpha, fixtures.user("new@example.com", TestFixtures.PASSWORD, false), "MEMBER");
		String token = fixtures.login(api.central(), "new@example.com");

		assertForbidden(api.central().postJson("/api/auth/handoff", "{\"slug\": \"alpha\"}", headers -> headers.setBearerAuth(token)));
	}

	@Test
	void codesAreStoredHashed() {
		String code = code(issue(api.central(), "alpha"));

		assertThat(OwnerSql.queryForString(
				"SELECT count(*) FROM bandcore.handoff_codes WHERE code_hash = sha256(convert_to('%s', 'UTF8'))".formatted(code)))
			.isEqualTo("1");
	}

	private String issue(ApiClient host, String slug) {
		String token = fixtures.login(host, "ana@example.com");
		String body = slug == null ? "{}" : "{\"slug\": \"%s\"}".formatted(slug);
		ResponseEntity<String> response = host.postJson("/api/auth/handoff", body, headers -> headers.setBearerAuth(token));
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return JsonPath.read(response.getBody(), "$.url");
	}

	private static ResponseEntity<String> exchange(ApiClient host, String code) {
		return host.send(HttpMethod.POST, "/api/auth/handoff/exchange", "{\"code\": \"%s\"}".formatted(code), headers -> {
		});
	}

	private static String code(String url) {
		return url.substring(url.indexOf("#code=") + "#code=".length());
	}

	private static void assertUnauthorized(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("UNAUTHENTICATED");
	}

	private static void assertForbidden(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("ACCESS_DENIED");
	}

}
