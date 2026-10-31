package com.burstintofire.bandcore.identity;

import java.util.Optional;
import java.util.UUID;

/** Read access to accounts for other modules. The users table itself stays inside identity. */
public interface Accounts {

	Optional<Account> find(UUID userId);

	record Account(UUID id, String email, String displayName, boolean emailVerified, boolean active) {

		@Override
		public String toString() {
			return "Account[id=" + id + "]";
		}

	}

}
