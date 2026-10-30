package com.burstintofire.bandcore.shared.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import com.burstintofire.bandcore.shared.logging.RequestId;

/**
 * Builds and enriches RFC 9457 Problem Details. Every problem carries a stable {@code code}
 * and the {@code requestId}, and never internal details such as stack traces or SQL.
 */
public final class Problems {

	public static final String CODE = "code";

	public static final String REQUEST_ID = "requestId";

	private Problems() {
	}

	public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title(status));
		problem.setProperty(CODE, code.name());
		return problem;
	}

	/** Adds the defaults (title, code, request id) without overwriting values already set. */
	static ProblemDetail enrich(ProblemDetail problem, HttpServletRequest request) {
		HttpStatusCode status = HttpStatusCode.valueOf(problem.getStatus());
		if (problem.getTitle() == null) {
			problem.setTitle(title(status));
		}
		if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
			problem.setProperty(CODE, ErrorCode.forStatus(status).name());
		}
		if (request != null && request.getAttribute(RequestId.ATTRIBUTE) instanceof String requestId) {
			problem.setProperty(REQUEST_ID, requestId);
		}
		return problem;
	}

	private static String title(HttpStatusCode status) {
		HttpStatus known = HttpStatus.resolve(status.value());
		return known != null ? known.getReasonPhrase() : "Error";
	}

}
