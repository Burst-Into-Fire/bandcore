package com.burstintofire.bandcore.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * The principal of an authenticated request, built after the token, the host and the
 * membership were validated.
 *
 * @param userId the account
 * @param bandId the band of the host, or null for a central session
 * @param role the role in that band, loaded from the database on every request, or null for a
 *        central session
 * @param emailVerified whether the account's email is verified, loaded on every request
 */
public record AuthenticatedUser(UUID userId, UUID bandId, String role, boolean emailVerified) {

	public AuthenticatedUser {
		Objects.requireNonNull(userId, "userId");
	}

	public boolean isCentral() {
		return bandId == null;
	}

}
