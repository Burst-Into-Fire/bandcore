package com.burstintofire.bandcore.identity.user;

import java.util.Locale;

/** One email, one account: {@code A@X.com} and {@code a@x.com} are the same account. */
public final class Emails {

	public static final int MAX_LENGTH = 254;

	private Emails() {
	}

	public static String normalize(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
