package com.burstintofire.bandcore.identity.token;

/** Claim names of the bandcore access token, besides the registered ones (sub, aud, iss, jti). */
public final class TokenClaims {

	/** The band the token was issued for; absent on central sessions. */
	public static final String BAND_ID = "band_id";

	/** The role at issue time. Only a hint: the role is loaded from the database on every request. */
	public static final String ROLE = "role";

	private TokenClaims() {
	}

}
