package com.burstintofire.bandcore.tenancy.invitation;

/** Stored status. "Expired" is not stored: a pending invitation past its expiry is expired. */
public enum InvitationStatus {

	PENDING, ACCEPTED, REVOKED

}
