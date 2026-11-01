package com.burstintofire.bandcore.identity.signup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestMailServer;
import com.burstintofire.bandcore.support.TestMailServer.Email;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;

@ExtendWith(OutputCaptureExtension.class)
class SignupIT extends IntegrationTest {

	static final String PASSWORD = "a long and unique passphrase";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BandRegistry bands;

	@Test
	void signUpVerifyAndTheAccountIsVerified(CapturedOutput output) {
		ResponseEntity<String> signup = signUp("ana@example.com", PASSWORD, "Ana");

		assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		Email email = TestMailServer.awaitEmail("ana@example.com");
		assertThat(email.subject()).isEqualTo("Confirme seu email no bandcore");
		assertThat(email.body()).contains("Olá, Ana!").contains("http://app.localhost:3000/verify-email#token=");
		assertThat(verifiedAt("ana@example.com")).isNull();

		ResponseEntity<String> verify = verify(email.token());

		assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(verifiedAt("ana@example.com")).isNotNull();
		assertThat(output.getOut()).doesNotContain(PASSWORD).doesNotContain(email.token()).doesNotContain("ana@example.com");
	}

	@Test
	void passwordIsStoredAsArgon2idHash() {
		signUp("ana@example.com", PASSWORD, "Ana");

		String hash = ownerQuery("SELECT password_hash FROM bandcore.users WHERE email = 'ana@example.com'");

		assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
	}

	@Test
	void verificationTokenIsSingleUse() {
		signUp("ana@example.com", PASSWORD, "Ana");
		String token = TestMailServer.awaitEmail("ana@example.com").token();
		verify(token);

		assertProblem(verify(token), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
	}

	@Test
	void expiredAndUnknownTokensAreRejectedTheSameWay() {
		signUp("ana@example.com", PASSWORD, "Ana");
		String token = TestMailServer.awaitEmail("ana@example.com").token();
		OwnerSql.global("UPDATE bandcore.email_verification_tokens SET expires_at = '%s'"
			.formatted(TestClockConfiguration.NOW.minusSeconds(1)));

		assertProblem(verify(token), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertProblem(verify("x".repeat(43)), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(verifiedAt("ana@example.com")).isNull();
	}

	@Test
	void expiryIsTwentyFourHours() {
		signUp("ana@example.com", PASSWORD, "Ana");

		String lifetime = ownerQuery(
				"SELECT extract(epoch FROM expires_at - created_at)::int FROM bandcore.email_verification_tokens");

		assertThat(lifetime).isEqualTo("86400");
	}

	@Test
	void duplicateEmailGetsTheSameResponseAndCreatesNoSecondAccount() {
		ResponseEntity<String> first = signUp("ana@example.com", PASSWORD, "Ana");
		verify(TestMailServer.awaitEmail("ana@example.com").token());
		TestMailServer.purge();

		ResponseEntity<String> second = signUp("ana@example.com", "another long passphrase", "Impostor");

		assertThat(second.getStatusCode()).isEqualTo(first.getStatusCode());
		assertThat(second.getBody()).isEqualTo(first.getBody());
		assertThat(ownerQuery("SELECT count(*) FROM bandcore.users")).isEqualTo("1");
		Email warning = TestMailServer.awaitEmail("ana@example.com");
		assertThat(warning.subject()).isEqualTo("Tentativa de cadastro com seu email no bandcore");
		assertThat(warning.body()).contains("Olá, Ana!").contains("http://app.localhost:3000/login");
	}

	@Test
	void emailsAreCaseInsensitive() {
		signUp("Ana@Example.COM", PASSWORD, "Ana");
		signUp("ana@example.com", PASSWORD, "Ana");

		assertThat(ownerQuery("SELECT count(*) FROM bandcore.users")).isEqualTo("1");
		assertThat(ownerQuery("SELECT email FROM bandcore.users")).isEqualTo("ana@example.com");
	}

	@Test
	void signingUpAgainBeforeVerifyingSendsANewLinkAndInvalidatesTheOldOne() {
		signUp("ana@example.com", PASSWORD, "Ana");
		String first = TestMailServer.awaitEmail("ana@example.com").token();
		TestMailServer.purge();

		signUp("ana@example.com", PASSWORD, "Ana");
		String second = TestMailServer.awaitEmail("ana@example.com").token();

		assertProblem(verify(first), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(verify(second).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void resendSendsANewLinkOnlyToUnverifiedAccounts() {
		signUp("ana@example.com", PASSWORD, "Ana");
		String first = TestMailServer.awaitEmail("ana@example.com").token();
		TestMailServer.purge();

		ResponseEntity<String> resend = resend("ANA@example.com");

		assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		String second = TestMailServer.awaitEmail("ana@example.com").token();
		assertThat(second).isNotEqualTo(first);
		assertProblem(verify(first), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		verify(second);
		TestMailServer.purge();

		ResponseEntity<String> afterVerification = resend("ana@example.com");
		ResponseEntity<String> unknown = resend("nobody@example.com");
		TestMailServer.settle();

		assertThat(afterVerification.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(unknown.getBody()).isEqualTo(afterVerification.getBody());
		assertThat(TestMailServer.emailsTo("ana@example.com")).isEmpty();
		assertThat(TestMailServer.emailsTo("nobody@example.com")).isEmpty();
	}

	@Test
	void weakPasswordsAreRejectedOnThePasswordField() {
		for (var weak : List.of(List.of("short", "PASSWORD_TOO_SHORT"), List.of("1qaz2wsx3edc", "PASSWORD_TOO_COMMON"),
				List.of("x".repeat(129), "PASSWORD_TOO_LONG"))) {
			ResponseEntity<String> response = signUp("ana@example.com", weak.get(0), "Ana");

			assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, weak.get(1));
			assertThat(JsonPath.<String>read(response.getBody(), "$.errors[0].field")).isEqualTo("password");
		}
		assertThat(ownerQuery("SELECT count(*) FROM bandcore.users")).isEqualTo("0");
	}

	@Test
	void invalidFieldsAreValidationProblems() {
		ResponseEntity<String> response = api.central()
			.postJson("/api/auth/signup", """
					{"email": "not-an-email", "password": "%s", "displayName": " "}
					""".formatted(PASSWORD));

		assertProblem(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		List<String> fields = JsonPath.read(response.getBody(), "$.errors[*].field");
		assertThat(fields).containsExactly("displayName", "email");
	}

	@Test
	void signupIsOnlyAvailableOnTheCentralHost() {
		bands.register("burst", "Burst");

		ResponseEntity<String> response = api.forBand("burst").postJson("/api/auth/signup", body("ana@example.com", PASSWORD, "Ana"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void signupWithoutTheCsrfHeaderIsForbidden() {
		ResponseEntity<String> response = api.central()
			.withoutCsrfHeader()
			.postJson("/api/auth/signup", body("ana@example.com", PASSWORD, "Ana"));

		assertProblem(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		assertThat(response.getHeaders().get("Set-Cookie")).isNull();
		assertThat(ownerQuery("SELECT count(*) FROM bandcore.users")).isEqualTo("0");
	}

	ResponseEntity<String> signUp(String email, String password, String name) {
		return api.central().postJson("/api/auth/signup", body(email, password, name));
	}

	ResponseEntity<String> verify(String token) {
		return api.central().postJson("/api/auth/verify-email", "{\"token\": \"%s\"}".formatted(token));
	}

	ResponseEntity<String> resend(String email) {
		return api.central().postJson("/api/auth/resend-verification", "{\"email\": \"%s\"}".formatted(email));
	}

	static String body(String email, String password, String name) {
		return """
				{"email": "%s", "password": "%s", "displayName": "%s"}
				""".formatted(email, password, name);
	}

	private String verifiedAt(String email) {
		return ownerQuery("SELECT email_verified_at FROM bandcore.users WHERE email = '" + email + "'");
	}

	static String ownerQuery(String sql) {
		try (var connection = com.burstintofire.bandcore.support.PostgresTestDatabase.ownerConnection();
				var statement = connection.createStatement();
				var rs = statement.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}
