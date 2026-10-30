package com.burstintofire.bandcore.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The authenticated user of the current request, if any. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<AuthenticatedUser> get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return Optional.of(user);
		}
		return Optional.empty();
	}

	public static Optional<UUID> id() {
		return get().map(AuthenticatedUser::userId);
	}

	/** The authenticated user; Spring Security guarantees one on non-public endpoints. */
	public static AuthenticatedUser require() {
		return get().orElseThrow(() -> new IllegalStateException("No authenticated user"));
	}

}
