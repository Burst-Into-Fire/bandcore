package com.burstintofire.bandcore.tenancy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The current band's members (integrantes), for other modules: finance records who paid and who
 * shares each expense. Always the band of the request; joins the caller's transaction.
 */
public interface BandMembers {

	/** The member linked to this account in the current band, if any. */
	Optional<UUID> memberOf(UUID userId);

	/**
	 * Whether each of these ids is an active member of the current band. Ids that are not members
	 * of the band (unknown, or another band's) are missing from the result.
	 */
	Map<UUID, Boolean> activeStatus(Collection<UUID> memberIds);

	/** Ids of the current band's active members, sorted by id (creation order). */
	List<UUID> activeMemberIds();

}
