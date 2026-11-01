package com.burstintofire.bandcore.shared.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * A rule violation reported to the client as Problem Details with a stable {@link ErrorCode}.
 * When {@code field} is set, the problem also lists it in {@code errors}, so forms can show
 * the message next to the field. The detail must never contain personal data or secrets.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final ErrorCode code;

	private final String field;

	private final HttpHeaders headers = new HttpHeaders();

	public ApiException(HttpStatus status, ErrorCode code, String detail) {
		this(status, code, detail, null);
	}

	public ApiException(HttpStatus status, ErrorCode code, String detail, String field) {
		super(detail, null, false, false);
		this.status = status;
		this.code = code;
		this.field = field;
	}

	public HttpStatus status() {
		return status;
	}

	public ErrorCode code() {
		return code;
	}

	public String field() {
		return field;
	}

	/** Extra response headers, for example a {@code Set-Cookie} that clears a dead session. */
	public HttpHeaders headers() {
		return headers;
	}

	public ApiException withHeader(String name, String value) {
		headers.add(name, value);
		return this;
	}

	/** 404 that reveals nothing, used for other bands' resources as well as missing ones. */
	public static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "The resource was not found.");
	}

}
