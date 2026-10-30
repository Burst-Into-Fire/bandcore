package com.burstintofire.bandcore.shared.web;

import org.springframework.http.HttpStatusCode;

/**
 * Stable, machine-readable error codes returned in the {@code code} property of every
 * Problem Details response. Clients (and translations in the web app) rely on these values,
 * so never rename one: add a new code instead.
 */
public enum ErrorCode {

	BAD_REQUEST,
	VALIDATION_FAILED,
	MALFORMED_REQUEST,
	UNAUTHENTICATED,
	ACCESS_DENIED,
	NOT_FOUND,
	METHOD_NOT_ALLOWED,
	NOT_ACCEPTABLE,
	CONFLICT,
	PAYLOAD_TOO_LARGE,
	UNSUPPORTED_MEDIA_TYPE,
	TOO_MANY_REQUESTS,
	INTERNAL_ERROR,
	SERVICE_UNAVAILABLE,

	// Bands
	SLUG_INVALID,
	SLUG_RESERVED,
	SLUG_TAKEN,

	// Accounts
	PASSWORD_TOO_SHORT,
	PASSWORD_TOO_LONG,
	PASSWORD_TOO_COMMON,
	/** Verification (and later reset, invitation) token unknown, used or expired: one code for all. */
	TOKEN_INVALID,
	/** Wrong email or password, unverified email or no access to the band: one code for all. */
	INVALID_CREDENTIALS,
	/** The action needs a verified email (creating a band, accepting an invitation). */
	EMAIL_NOT_VERIFIED,
	BAND_LIMIT_REACHED;

	/** Default code for a status when no more specific code applies. */
	public static ErrorCode forStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> BAD_REQUEST;
			case 401 -> UNAUTHENTICATED;
			case 403 -> ACCESS_DENIED;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 406 -> NOT_ACCEPTABLE;
			case 409 -> CONFLICT;
			case 413 -> PAYLOAD_TOO_LARGE;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			case 429 -> TOO_MANY_REQUESTS;
			case 503 -> SERVICE_UNAVAILABLE;
			default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
		};
	}

}
