package com.burstintofire.bandcore.identity.token;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfiguration {

	private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);

	/** Tolerated difference between servers' clocks when checking {@code exp} and {@code nbf}. */
	static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		return signingKey(properties.secret());
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey, JwtProperties properties) {
		OctetSequenceKey key = new OctetSequenceKey.Builder(jwtSigningKey).keyID(properties.keyId())
			.algorithm(JWSAlgorithm.HS256)
			.build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
	}

	/**
	 * Accepts only HS256 with the server key ({@code alg: none} and other algorithms are
	 * rejected), checks {@code exp}/{@code nbf} against the application clock and the issuer.
	 * The audience and band are checked against the request host by {@link AccessTokenAuthenticator}.
	 */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}

	static SecretKey signingKey(String secret) {
		if (secret == null || secret.isBlank()) {
			// Only reachable outside production: the prod profile refuses to start without JWT_SECRET.
			log.warn("JWT_SECRET is not set: using a random key, so sessions end when the API restarts");
			byte[] random = new byte[48];
			new SecureRandom().nextBytes(random);
			return new SecretKeySpec(random, "HmacSHA256");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < JwtProperties.MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must have at least " + JwtProperties.MIN_SECRET_BYTES
					+ " bytes (256 bits); generate one with: openssl rand -base64 48");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
