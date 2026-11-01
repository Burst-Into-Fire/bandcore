package com.burstintofire.bandcore.tenancy.membership;

public enum MembershipStatus {

	ACTIVE,
	/** Access removed; the row stays for history and so the user can be invited again. */
	REMOVED

}
