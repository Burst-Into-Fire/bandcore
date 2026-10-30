package com.burstintofire.bandcore.shared.logging;

/** Keeps personal data out of logs while leaving enough to correlate events. */
public final class LogMasking {

	private LogMasking() {
	}

	/** {@code ana.souza@example.com} → {@code a***@example.com}. */
	public static String email(String email) {
		if (email == null) {
			return "null";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

}
