package com.burstintofire.bandcore.identity.reset;

/** Published inside the transaction; the email is sent only after it commits. */
record PasswordResetRequested(String email, String displayName, String token) {

	@Override
	public String toString() {
		return "PasswordResetRequested[]";
	}

}
