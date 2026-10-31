package com.burstintofire.bandcore.shared.security;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/**
 * CSRF protection for a same-origin API without CORS: an unsafe request that carries no bearer
 * token (sign up, login, refresh and logout, which rely on the refresh cookie or create one)
 * must send {@value #NAME}. A cross-site form or image cannot set a custom header, and a
 * cross-site script cannot either without a CORS preflight, which this API never allows.
 *
 * <p>Spring Security's CSRF filter stays enabled: it rejects every request that needs
 * protection, because the token repository below never holds a token. The only way through is
 * the custom header (or a bearer token).
 */
public final class CsrfHeader {

	public static final String NAME = "X-Requested-With";

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private CsrfHeader() {
	}

	public static boolean requiresProtection(HttpServletRequest request) {
		if (SAFE_METHODS.contains(request.getMethod())) {
			return false;
		}
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		boolean bearer = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
		boolean customHeader = request.getHeader(NAME) != null && !request.getHeader(NAME).isBlank();
		return !bearer && !customHeader;
	}

	/** Never stores a token (and never creates an HTTP session), so requests that need one fail. */
	static final class NoTokenRepository implements CsrfTokenRepository {

		@Override
		public CsrfToken generateToken(HttpServletRequest request) {
			return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", UUID.randomUUID().toString());
		}

		@Override
		public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
		}

		@Override
		public CsrfToken loadToken(HttpServletRequest request) {
			return null;
		}

	}

}
