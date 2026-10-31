package com.burstintofire.bandcore.identity.signup;

/** Someone signed up with an email that already has a verified account. */
record ExistingAccountSignupAttempted(String email, String displayName) {

	@Override
	public String toString() {
		return "ExistingAccountSignupAttempted[]";
	}

}
