package com.burstintofire.bandcore.identity.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HS256 access tokens scoped to one host (and its band). */
@Service
public class AccessTokenService {

	public record AccessToken(String value, Instant expiresAt) {

		@Override
		public String toString() {
			return "AccessToken[expiresAt=" + expiresAt + "]";
		}

	}

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	public AccessTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param host the normalized host the token is valid on ({@code aud})
	 * @param bandId the band of that host, or null for a central session
	 * @param role the role in the band, or null for a central session
	 */
	public AccessToken issue(UUID userId, String host, UUID bandId, String role) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.accessTokenLifetime());
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(userId.toString())
			.audience(List.of(host))
			.id(UUID.randomUUID().toString())
			.issuedAt(now)
			.notBefore(now)
			.expiresAt(expiresAt);
		if (bandId != null) {
			claims.claim(TokenClaims.BAND_ID, bandId.toString()).claim(TokenClaims.ROLE, role);
		}
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(properties.keyId()).type("JWT").build();
		String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
		return new AccessToken(value, expiresAt);
	}

}
