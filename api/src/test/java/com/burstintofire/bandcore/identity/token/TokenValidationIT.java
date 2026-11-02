package com.burstintofire.bandcore.identity.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestTokens;

/**
 * A valid signature is not enough: the token must belong to the host's band, and the account
 * and membership are re-checked against the database on every request.
 */
class TokenValidationIT extends IntegrationTest {

	@Autowired
	JwtEncoder encoder;

	TestTokens tokens;

	UUID alpha;

	UUID beta;

	UUID ana;

	UUID anaMembership;

	ApiClient alphaHost;

	@BeforeEach
	void memberOfAlpha() {
		tokens = new TestTokens(encoder);
		alpha = fixtures.band("alpha");
		beta = fixtures.band("beta");
		ana = fixtures.verifiedUser("ana@example.com");
		anaMembership = fixtures.membership(alpha, ana, "ADMIN");
		alphaHost = api.forBand("alpha");
	}

	@Test
	void tokenIssuedByLoginIsAccepted() {
		assertThat(me(alphaHost, fixtures.login(alphaHost, "ana@example.com")).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void tokenOfOneBandIsRejectedOnAnotherBandsHost() {
		fixtures.membership(beta, ana, "ADMIN");
		String alphaToken = fixtures.login(alphaHost, "ana@example.com");

		assertUnauthorized(me(api.forBand("beta"), alphaToken));
	}

	@Test
	void tokenWithTheRightBandButAnotherAudienceIsRejected() {
		String token = tokens.signed(ana, "beta.localhost", alpha, claims -> {
		});

		assertUnauthorized(me(alphaHost, token));
	}

	@Test
	void centralAndBandTokensOnlyWorkOnTheirOwnKindOfHost() {
		String central = fixtures.login(api.central(), "ana@example.com");
		String band = fixtures.login(alphaHost, "ana@example.com");

		assertUnauthorized(me(alphaHost, central));
		assertUnauthorized(me(api.central(), band));
		// A central-audience token that claims a band is rejected too.
		assertUnauthorized(me(api.central(), tokens.signed(ana, "app.localhost", alpha, claims -> {
		})));
	}

	@Test
	void tamperedBandClaimIsRejected() {
		fixtures.membership(beta, ana, "MEMBER");
		String token = fixtures.login(alphaHost, "ana@example.com");

		String tampered = TestTokens.withClaim(token, "band_id", beta.toString());

		assertUnauthorized(me(api.forBand("beta"), TestTokens.withClaim(tampered, "aud", "beta.localhost")));
		assertUnauthorized(me(alphaHost, tampered));
	}

	@Test
	void unsignedTokenIsRejected() {
		String token = fixtures.login(alphaHost, "ana@example.com");

		assertUnauthorized(me(alphaHost, TestTokens.unsigned(token)));
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		SecretKeySpec otherKey = new SecretKeySpec("another-secret-key-with-32-bytes!!".getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		JwtEncoder forger = new NimbusJwtEncoder(
				new ImmutableJWKSet<SecurityContext>(new JWKSet(new OctetSequenceKey.Builder(otherKey).keyID("k1").build())));

		assertUnauthorized(me(alphaHost, new TestTokens(forger).signed(ana, "alpha.localhost", alpha, claims -> {
		})));
	}

	@Test
	void expiredTokenIsRejected() {
		String token = tokens.signed(ana, "alpha.localhost", alpha,
				claims -> claims.issuedAt(TestClockConfiguration.NOW.minusSeconds(1000))
					.expiresAt(TestClockConfiguration.NOW.minusSeconds(60)));

		assertUnauthorized(me(alphaHost, token));
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() {
		assertUnauthorized(me(alphaHost, tokens.signed(ana, "alpha.localhost", alpha, claims -> claims.issuer("evil"))));
	}

	@Test
	void removedMemberLosesAccessOnTheNextRequestEvenWithAnUnexpiredToken() {
		String token = fixtures.login(alphaHost, "ana@example.com");
		assertThat(me(alphaHost, token).getStatusCode()).isEqualTo(HttpStatus.OK);

		asAnotherAdmin(admin -> alphaHost.send(HttpMethod.DELETE, "/api/memberships/" + anaMembership, null, admin));

		assertUnauthorized(me(alphaHost, token));
	}

	@Test
	void downgradedRoleTakesEffectOnTheNextRequest() {
		String token = fixtures.login(alphaHost, "ana@example.com");
		assertThat(JsonPath.<String>read(me(alphaHost, token).getBody(), "$.band.role")).isEqualTo("ADMIN");

		asAnotherAdmin(admin -> alphaHost.patchJson("/api/memberships/" + anaMembership, "{\"role\": \"READ_ONLY\"}", admin));

		assertThat(JsonPath.<String>read(me(alphaHost, token).getBody(), "$.band.role")).isEqualTo("READ_ONLY");
	}

	@Test
	void disabledAccountLosesAccessOnTheNextRequest() {
		String token = fixtures.login(alphaHost, "ana@example.com");

		OwnerSql.global("UPDATE bandcore.users SET status = 'DISABLED' WHERE id = '" + ana + "'");

		assertUnauthorized(me(alphaHost, token));
	}

	@Test
	void garbageTokensAreRejected() {
		assertUnauthorized(me(alphaHost, "not-a-jwt"));
		assertUnauthorized(me(alphaHost, "a.b.c"));
	}

	/** Changes ana's membership through the API, as a second admin of alpha. */
	private void asAnotherAdmin(Function<Consumer<HttpHeaders>, ResponseEntity<String>> change) {
		fixtures.membership(alpha, fixtures.verifiedUser("bob@example.com"), "ADMIN");
		String bob = fixtures.login(alphaHost, "bob@example.com");
		ResponseEntity<String> response = change.apply(headers -> headers.setBearerAuth(bob));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	private static ResponseEntity<String> me(ApiClient client, String token) {
		return client.get("/api/me", headers -> headers.setBearerAuth(token));
	}

	private static void assertUnauthorized(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("UNAUTHENTICATED");
		assertThat(response.getBody()).doesNotContain("band_id").doesNotContain("signature").doesNotContain("expired");
	}

}
