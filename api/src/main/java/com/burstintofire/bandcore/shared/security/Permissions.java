package com.burstintofire.bandcore.shared.security;

import java.util.List;

/**
 * Named permissions checked by use cases with {@code @PreAuthorize("hasAuthority('...')")}.
 * Roles map to these in one place ({@code tenancy.Role}); the matrix is documented in
 * docs/security/permissions.md and proven by {@code AuthorizationMatrixIT}.
 *
 * <p>Rules about ownership ("members edit only their own expenses") are not permissions: they
 * are checked in the service layer on top of the permission.
 */
public final class Permissions {

	public static final String BAND_READ = "band:read";

	public static final String BAND_MANAGE = "band:manage";

	public static final String MEMBER_READ = "member:read";

	public static final String MEMBER_MANAGE = "member:manage";

	public static final String FINANCE_READ = "finance:read";

	/** Create expenses and settlements; edit, delete and mark as paid one's own. */
	public static final String FINANCE_WRITE = "finance:write";

	/** Edit, delete and mark as paid anyone's expenses and settlements. */
	public static final String FINANCE_MANAGE = "finance:manage";

	public static final String MERCH_READ = "merch:read";

	/** Create events and register sales. */
	public static final String SALE_WRITE = "sale:write";

	/** Refund sales; members only while the event is open (checked in the service). */
	public static final String SALE_REFUND = "sale:refund";

	/** Products, variants, production batches and stock adjustments. */
	public static final String MERCH_MANAGE = "merch:manage";

	/** Close events and resolve pending reconciliations. */
	public static final String EVENT_CLOSE = "event:close";

	public static final String AUDIT_READ = "audit:read";

	public static final List<String> ALL = List.of(BAND_READ, BAND_MANAGE, MEMBER_READ, MEMBER_MANAGE, FINANCE_READ,
			FINANCE_WRITE, FINANCE_MANAGE, MERCH_READ, SALE_WRITE, SALE_REFUND, MERCH_MANAGE, EVENT_CLOSE, AUDIT_READ);

	private static final String HAS = "hasAuthority('";

	private static final String END = "')";

	/** Expressions for {@code @PreAuthorize}. */
	public static final String CAN_READ_BAND = HAS + BAND_READ + END;

	public static final String CAN_MANAGE_BAND = HAS + BAND_MANAGE + END;

	public static final String CAN_READ_MEMBERS = HAS + MEMBER_READ + END;

	private Permissions() {
	}

}
