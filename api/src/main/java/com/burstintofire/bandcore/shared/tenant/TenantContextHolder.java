package com.burstintofire.bandcore.shared.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Holds the host and the band of the current request (set by the tenant resolution filter
 * before authentication) or of the current job ({@link #callAs}). The row-level security
 * context of every transaction is taken from here.
 *
 * <p>The tenant never comes from the request body, query string or arbitrary headers.
 */
public final class TenantContextHolder {

	/**
	 * @param host normalized host of the request ({@code burst.bandcore.app}), or null in jobs
	 * @param tenant the band, or null on the central host
	 */
	public record Scope(String host, TenantContext tenant) {

		public boolean isCentral() {
			return host != null && tenant == null;
		}

	}

	private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

	private TenantContextHolder() {
	}

	/** The band of the current request or job; empty on the central host or outside a scope. */
	public static Optional<TenantContext> current() {
		Scope scope = CURRENT.get();
		return scope == null ? Optional.empty() : Optional.ofNullable(scope.tenant());
	}

	/** The band of the current request or job; fails when there is none. */
	public static TenantContext require() {
		return current().orElseThrow(() -> new IllegalStateException("No band in the current scope"));
	}

	public static Optional<Scope> scope() {
		return Optional.ofNullable(CURRENT.get());
	}

	public static void set(Scope scope) {
		CURRENT.set(scope);
	}

	public static void clear() {
		CURRENT.remove();
	}

	/**
	 * Runs an action for one band, typically in a background job that processes several bands:
	 * one call (and one transaction) per band. The previous scope is restored afterwards.
	 */
	public static <T> T callAs(TenantContext tenant, Supplier<T> action) {
		Scope previous = CURRENT.get();
		CURRENT.set(new Scope(null, tenant));
		try {
			return action.get();
		}
		finally {
			if (previous == null) {
				CURRENT.remove();
			}
			else {
				CURRENT.set(previous);
			}
		}
	}

}
