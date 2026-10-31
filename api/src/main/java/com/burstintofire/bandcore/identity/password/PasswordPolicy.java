package com.burstintofire.bandcore.identity.password;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * NIST SP 800-63B style policy: 12 to 128 characters, no composition rules (any character,
 * spaces included), and not in the list of common passwords.
 */
@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 12;

	public static final int MAX_LENGTH = 128;

	private final CommonPasswords commonPasswords;

	public PasswordPolicy() {
		this(CommonPasswords.load());
	}

	PasswordPolicy(CommonPasswords commonPasswords) {
		this.commonPasswords = commonPasswords;
	}

	/** Throws a 422 problem on the {@code password} field when the password is not acceptable. */
	public void check(String password) {
		int length = password == null ? 0 : password.codePointCount(0, password.length());
		if (length < MIN_LENGTH) {
			throw problem(ErrorCode.PASSWORD_TOO_SHORT, "Use at least " + MIN_LENGTH + " characters.");
		}
		if (length > MAX_LENGTH) {
			throw problem(ErrorCode.PASSWORD_TOO_LONG, "Use at most " + MAX_LENGTH + " characters.");
		}
		if (commonPasswords.contains(password)) {
			throw problem(ErrorCode.PASSWORD_TOO_COMMON, "This password is too common.");
		}
	}

	private static ApiException problem(ErrorCode code, String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, detail, "password");
	}

}
