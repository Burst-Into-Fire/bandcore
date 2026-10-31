package com.burstintofire.bandcore.tenancy;

import static com.burstintofire.bandcore.shared.security.Permissions.BAND_READ;
import static com.burstintofire.bandcore.shared.security.Permissions.FINANCE_READ;
import static com.burstintofire.bandcore.shared.security.Permissions.FINANCE_WRITE;
import static com.burstintofire.bandcore.shared.security.Permissions.MEMBER_READ;
import static com.burstintofire.bandcore.shared.security.Permissions.MERCH_READ;
import static com.burstintofire.bandcore.shared.security.Permissions.SALE_REFUND;
import static com.burstintofire.bandcore.shared.security.Permissions.SALE_WRITE;

import java.util.Set;

import com.burstintofire.bandcore.shared.security.Permissions;

/**
 * A user's role in one band. The same user can have a different role in each band. This is the
 * only place where roles map to permissions (docs/security/permissions.md).
 */
public enum Role {

	/** Every permission, including the admin-only ones (band:manage, member:manage, finance:manage,
	 * merch:manage, event:close, audit:read). */
	ADMIN(Set.copyOf(Permissions.ALL)),

	MEMBER(Set.of(BAND_READ, MEMBER_READ, FINANCE_READ, FINANCE_WRITE, MERCH_READ, SALE_WRITE, SALE_REFUND)),

	/** Sees everything a member sees and changes nothing. */
	READ_ONLY(Set.of(BAND_READ, MEMBER_READ, FINANCE_READ, MERCH_READ));

	private final Set<String> permissions;

	Role(Set<String> permissions) {
		this.permissions = permissions;
	}

	public Set<String> permissions() {
		return permissions;
	}

}
