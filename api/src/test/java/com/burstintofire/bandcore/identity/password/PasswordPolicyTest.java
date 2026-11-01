package com.burstintofire.bandcore.identity.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

class PasswordPolicyTest {

	private static final PasswordPolicy POLICY = new PasswordPolicy();

	@ParameterizedTest
	@ValueSource(strings = { "correct horse battery", "Banda no palco 2026", "aaaaaaaaaaab", "senha sem regras",
			"🎸🎸🎸🎸🎸🎸🎸🎸🎸🎸🎸🎸" })
	void acceptsLongUncommonPasswordsWithoutCompositionRules(String password) {
		assertThatCode(() -> POLICY.check(password)).doesNotThrowAnyException();
	}

	@Test
	void rejectsShortPasswords() {
		assertCode("short pass1", ErrorCode.PASSWORD_TOO_SHORT);
		assertCode("", ErrorCode.PASSWORD_TOO_SHORT);
		assertCode(null, ErrorCode.PASSWORD_TOO_SHORT);
	}

	@Test
	void countsCharactersNotUtf16Units() {
		// 11 emoji are 22 UTF-16 units but only 11 characters.
		assertCode("🎸".repeat(11), ErrorCode.PASSWORD_TOO_SHORT);
	}

	@Test
	void acceptsUpTo128Characters() {
		assertThatCode(() -> POLICY.check("x".repeat(127) + "y")).doesNotThrowAnyException();
		assertCode("x".repeat(129), ErrorCode.PASSWORD_TOO_LONG);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1qaz2wsx3edc", "qwerty123456", "Qwerty123456", "123456789012" })
	void rejectsCommonPasswordsRegardlessOfCase(String password) {
		assertCode(password, ErrorCode.PASSWORD_TOO_COMMON);
	}

	@Test
	void bundledListIsLoaded() {
		assertThat(CommonPasswords.load().size()).isGreaterThan(5000);
	}

	private static void assertCode(String password, ErrorCode code) {
		assertThatThrownBy(() -> POLICY.check(password)).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.code()).isEqualTo(code);
			assertThat(ex.field()).isEqualTo("password");
		});
	}

}
