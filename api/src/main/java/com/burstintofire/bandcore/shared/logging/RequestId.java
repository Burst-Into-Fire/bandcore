package com.burstintofire.bandcore.shared.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request correlation id, propagated through the {@value #HEADER} header and stored in the
 * logging MDC under {@value #MDC_KEY}.
 */
public final class RequestId {

	public static final String HEADER = "X-Request-Id";

	public static final String MDC_KEY = "requestId";

	/** Request attribute holding the id, still available during error dispatches. */
	public static final String ATTRIBUTE = RequestId.class.getName();

	static final int MAX_LENGTH = 64;

	private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_LENGTH + "}");

	private RequestId() {
	}

	/**
	 * Returns the incoming id when it is safe to log and echo back, or a new random id
	 * otherwise. Rejecting unsafe values prevents log injection and header splitting.
	 */
	public static String resolve(String incoming) {
		if (incoming != null && SAFE.matcher(incoming).matches()) {
			return incoming;
		}
		return UUID.randomUUID().toString();
	}

}
