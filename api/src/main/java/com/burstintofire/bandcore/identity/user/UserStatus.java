package com.burstintofire.bandcore.identity.user;

public enum UserStatus {

	ACTIVE,
	/** Cannot log in or refresh; existing access tokens stop working on the next request. */
	DISABLED

}
