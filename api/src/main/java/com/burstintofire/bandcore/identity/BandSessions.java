package com.burstintofire.bandcore.identity;

import java.util.UUID;

/** Ends a user's sessions in one band, for other modules (removing someone's access). */
public interface BandSessions {

	/** Revokes the user's refresh tokens on that band's host; other bands are untouched. */
	void endSessionsInBand(UUID userId, UUID bandId);

}
