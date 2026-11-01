package com.burstintofire.bandcore.support;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Reads the refresh token cookie from responses and sends it back, like a browser would. */
public final class Cookies {

	public static final String REFRESH = "__Host-bandcore_rt";

	private Cookies() {
	}

	/** The full Set-Cookie header for the refresh token, or null. */
	public static String refreshSetCookie(ResponseEntity<?> response) {
		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		if (cookies == null) {
			return null;
		}
		return cookies.stream().filter(cookie -> cookie.startsWith(REFRESH + "=")).findFirst().orElse(null);
	}

	/** The refresh token value set by the response. */
	public static String refreshToken(ResponseEntity<?> response) {
		String header = refreshSetCookie(response);
		if (header == null) {
			throw new AssertionError("No refresh token cookie in " + response.getHeaders());
		}
		return header.substring(REFRESH.length() + 1, header.indexOf(';'));
	}

	public static void send(HttpHeaders headers, String refreshToken) {
		headers.add(HttpHeaders.COOKIE, REFRESH + "=" + refreshToken);
	}

	public static void bearer(HttpHeaders headers, String accessToken) {
		headers.setBearerAuth(accessToken);
	}

}
