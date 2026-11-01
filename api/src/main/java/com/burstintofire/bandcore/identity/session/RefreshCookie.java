package com.burstintofire.bandcore.identity.session;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;

/**
 * The refresh token cookie. {@code __Host-} makes the browser enforce {@code Secure},
 * {@code Path=/} and no {@code Domain}, so the cookie never leaves the band's own host.
 * Browsers treat {@code *.localhost} as a secure context, so it also works over local HTTP.
 */
public final class RefreshCookie {

	public static final String NAME = "__Host-bandcore_rt";

	private RefreshCookie() {
	}

	public static ResponseCookie of(String token, Duration maxAge) {
		return base(token).maxAge(maxAge).build();
	}

	public static ResponseCookie cleared() {
		return base("").maxAge(Duration.ZERO).build();
	}

	public static Optional<String> read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
				return Optional.of(cookie.getValue());
			}
		}
		return Optional.empty();
	}

	private static ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Strict").path("/");
	}

}
