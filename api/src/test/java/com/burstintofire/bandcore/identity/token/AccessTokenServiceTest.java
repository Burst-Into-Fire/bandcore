package com.burstintofire.bandcore.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class AccessTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-15T15:00:00Z");

	private static final JwtProperties PROPERTIES = new JwtProperties("0123456789abcdef0123456789abcdef", "k7",
			"bandcore", null, null, null);

	private static final UUID USER = UUID.fromString("0190c000-0000-7000-8000-000000000001");

	private static final UUID BAND = UUID.fromString("0190a000-0000-7000-8000-00000000000a");

	private final JwtConfiguration configuration = new JwtConfiguration();

	private final SecretKey key = configuration.jwtSigningKey(PROPERTIES);

	@Test
	void bandTokenCarriesTheBandRoleAudienceAndFifteenMinuteLifetime() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccessTokenService service = new AccessTokenService(configuration.jwtEncoder(key, PROPERTIES), PROPERTIES, clock);

		AccessTokenService.AccessToken token = service.issue(USER, "burst.bandcore.app", BAND, "ADMIN");
		Jwt jwt = decoder(clock).decode(token.value());

		assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(jwt.getSubject()).isEqualTo(USER.toString());
		assertThat(jwt.getAudience()).containsExactly("burst.bandcore.app");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("bandcore");
		assertThat(jwt.getClaimAsString(TokenClaims.BAND_ID)).isEqualTo(BAND.toString());
		assertThat(jwt.getClaimAsString(TokenClaims.ROLE)).isEqualTo("ADMIN");
		assertThat(jwt.getId()).isNotBlank();
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(jwt.getHeaders()).containsEntry("kid", "k7").containsEntry("alg", "HS256");
	}

	@Test
	void centralTokenHasNoBandAndNoRole() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccessTokenService service = new AccessTokenService(configuration.jwtEncoder(key, PROPERTIES), PROPERTIES, clock);

		Jwt jwt = decoder(clock).decode(service.issue(USER, "app.bandcore.app", null, null).value());

		assertThat(jwt.getClaims()).doesNotContainKeys(TokenClaims.BAND_ID, TokenClaims.ROLE);
		assertThat(jwt.getAudience()).isEqualTo(List.of("app.bandcore.app"));
	}

	@Test
	void everyTokenHasItsOwnId() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccessTokenService service = new AccessTokenService(configuration.jwtEncoder(key, PROPERTIES), PROPERTIES, clock);

		String first = decoder(clock).decode(service.issue(USER, "a.localhost", BAND, "ADMIN").value()).getId();
		String second = decoder(clock).decode(service.issue(USER, "a.localhost", BAND, "ADMIN").value()).getId();

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void tokenIsRejectedAfterItExpires() {
		AccessTokenService service = new AccessTokenService(configuration.jwtEncoder(key, PROPERTIES), PROPERTIES,
				Clock.fixed(NOW, ZoneOffset.UTC));
		String token = service.issue(USER, "burst.bandcore.app", BAND, "ADMIN").value();
		Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(15)).plus(JwtConfiguration.CLOCK_SKEW).plusSeconds(1),
				ZoneOffset.UTC);

		assertThat(decoder(Clock.fixed(NOW.plus(Duration.ofMinutes(14)), ZoneOffset.UTC)).decode(token)).isNotNull();
		assertThatThrownBy(() -> decoder(later).decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void secretMustHaveAtLeast256Bits() {
		assertThatThrownBy(() -> JwtConfiguration.signingKey("too short")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes")
			.hasMessageNotContaining("too short");
		assertThat(JwtConfiguration.signingKey("").getEncoded()).hasSizeGreaterThanOrEqualTo(32);
	}

	private JwtDecoder decoder(Clock clock) {
		return configuration.jwtDecoder(key, PROPERTIES, clock);
	}

}
