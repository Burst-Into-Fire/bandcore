package com.burstintofire.bandcore.identity.reset;

/** The password was reset; the owner gets a confirmation email. */
record PasswordChanged(String email, String displayName) {

	@Override
	public String toString() {
		return "PasswordChanged[]";
	}

}
