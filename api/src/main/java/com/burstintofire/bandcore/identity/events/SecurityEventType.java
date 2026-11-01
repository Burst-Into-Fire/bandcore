package com.burstintofire.bandcore.identity.events;

/** Kinds of account security events; the database CHECK constraint lists the same values. */
public enum SecurityEventType {

	SIGNUP,
	EMAIL_VERIFIED,
	LOGIN_SUCCEEDED,
	LOGIN_FAILED,
	LOGIN_RATE_LIMITED,
	REFRESH_TOKEN_REUSED,
	LOGOUT_ALL,
	PASSWORD_RESET_REQUESTED,
	PASSWORD_RESET_COMPLETED

}
