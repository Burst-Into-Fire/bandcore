package com.burstintofire.bandcore.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read access to accounts for other modules. The users table itself stays inside identity. */
public interface Accounts {

	Optional<Account> find(UUID userId);

	/** The accounts that exist among these ids, in no particular order. */
	List<Account> findAll(Collection<UUID> userIds);

	/** The account with this email, compared after {@link #normalizeEmail normalization}. */
	Optional<Account> findByEmail(String email);

	/** Emails are stored trimmed and in lowercase; compare them only after this. */
	static String normalizeEmail(String email) {
		return com.burstintofire.bandcore.identity.user.Emails.normalize(email);
	}

	record Account(UUID id, String email, String displayName, boolean emailVerified, boolean active) {

		@Override
		public String toString() {
			return "Account[id=" + id + "]";
		}

	}

}
