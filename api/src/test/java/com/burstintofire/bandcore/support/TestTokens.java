package com.burstintofire.bandcore.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Builds access tokens with arbitrary claims, signed with the application key, and forgeries. */
public final class TestTokens {

	private final JwtEncoder encoder;

	public TestTokens(JwtEncoder encoder) {
		this.encoder = encoder;
	}

	/** A token like the application issues at {@link TestClockConfiguration#NOW}, adjusted by {@code claims}. */
	public String signed(UUID userId, String host, UUID bandId, Consumer<JwtClaimsSet.Builder> claims) {
		Instant now = TestClockConfiguration.NOW;
		JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
			.issuer("bandcore")
			.subject(userId.toString())
			.audience(List.of(host))
			.id(UUID.randomUUID().toString())
			.issuedAt(now)
			.expiresAt(now.plusSeconds(900));
		if (bandId != null) {
			builder.claim("band_id", bandId.toString()).claim("role", "ADMIN");
		}
		claims.accept(builder);
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId("k1").build();
		return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
	}

	/** Same header and signature, with one claim replaced in the payload (a tampered token). */
	public static String withClaim(String token, String claim, String newValue) {
		String[] parts = token.split("\\.");
		String payload = decode(parts[1]).replaceAll("\"" + claim + "\":\"[^\"]*\"", "\"" + claim + "\":\"" + newValue + "\"");
		return parts[0] + "." + encode(payload) + "." + parts[2];
	}

	/** The same payload with {@code alg: none} and no signature. */
	public static String unsigned(String token) {
		String[] parts = token.split("\\.");
		return encode("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + parts[1] + ".";
	}

	private static String decode(String part) {
		return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
	}

	private static String encode(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
