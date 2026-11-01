package com.burstintofire.bandcore.identity.token;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret HS256 key ({@code JWT_SECRET}), at least 32 bytes; required in production
 * @param keyId {@code kid} header, so a second key can be added during rotation
 * @param issuer {@code iss} claim
 * @param accessTokenLifetime access tokens live in memory only and cannot be revoked: keep short
 * @param refreshTokenIdleLifetime sliding expiry, renewed on each refresh
 * @param refreshTokenAbsoluteLifetime hard limit of a refresh token family since login
 */
@ConfigurationProperties("bandcore.security.jwt")
public record JwtProperties(String secret, String keyId, String issuer, Duration accessTokenLifetime,
		Duration refreshTokenIdleLifetime, Duration refreshTokenAbsoluteLifetime) {

	public static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		keyId = keyId == null || keyId.isBlank() ? "k1" : keyId;
		issuer = issuer == null || issuer.isBlank() ? "bandcore" : issuer;
		accessTokenLifetime = accessTokenLifetime == null ? Duration.ofMinutes(15) : accessTokenLifetime;
		refreshTokenIdleLifetime = refreshTokenIdleLifetime == null ? Duration.ofDays(14) : refreshTokenIdleLifetime;
		refreshTokenAbsoluteLifetime = refreshTokenAbsoluteLifetime == null ? Duration.ofDays(30)
				: refreshTokenAbsoluteLifetime;
	}

	@Override
	public String toString() {
		return "JwtProperties[keyId=" + keyId + ", issuer=" + issuer + "]";
	}

}
