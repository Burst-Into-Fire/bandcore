package com.burstintofire.bandcore.identity.signup;

/** Published inside the transaction; the email is sent only after it commits. */
record VerificationEmailRequested(String email, String displayName, String token) {

	@Override
	public String toString() {
		return "VerificationEmailRequested[]";
	}

}
