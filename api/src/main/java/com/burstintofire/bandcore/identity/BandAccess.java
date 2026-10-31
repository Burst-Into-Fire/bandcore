package com.burstintofire.bandcore.identity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a user may do in a band, provided by the tenancy module (which owns memberships). The
 * identity module asks at login, at refresh and on every authenticated request on a band host.
 */
public interface BandAccess {

	/** The user's active access to the band, or empty when there is none. */
	Optional<Grant> find(UUID bandId, UUID userId);

	/**
	 * @param role the membership role ({@code ADMIN}, {@code MEMBER}, {@code READ_ONLY})
	 * @param permissions named permissions granted by the role ({@code expense:write}, ...)
	 */
	record Grant(String role, Set<String> permissions) {
	}

}
