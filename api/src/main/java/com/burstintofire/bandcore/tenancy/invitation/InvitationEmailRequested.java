package com.burstintofire.bandcore.tenancy.invitation;

import com.burstintofire.bandcore.tenancy.Role;

/** Published inside the transaction; the email is sent only after it commits. */
record InvitationEmailRequested(String email, String bandName, String inviterName, Role role, String token) {

	@Override
	public String toString() {
		return "InvitationEmailRequested[]";
	}

}
