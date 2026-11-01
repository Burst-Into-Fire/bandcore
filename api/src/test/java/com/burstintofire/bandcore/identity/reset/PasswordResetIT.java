package com.burstintofire.bandcore.identity.reset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.Cookies;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestFixtures;
import com.burstintofire.bandcore.support.TestMailServer;
import com.burstintofire.bandcore.support.TestMailServer.Email;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetIT extends IntegrationTest {

	static final String NEW_PASSWORD = "a brand new passphrase";

	static final String RESET_SUBJECT = "Redefina sua senha do bandcore";

	@MockitoSpyBean
	ResetTokenGenerator generator;

	UUID alpha;

	@BeforeEach
	void memberOfAlpha() {
		alpha = fixtures.band("alpha");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "MEMBER");
	}

	@Test
	void requestSendsALinkAndTheNewPasswordWorks(CapturedOutput output) {
		ResponseEntity<String> request = requestReset("ANA@example.com");

		assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		Email email = TestMailServer.awaitEmail("ana@example.com", RESET_SUBJECT);
		assertThat(email.body()).contains("Olá, Ana!")
			.contains("http://app.localhost:3000/reset-password#token=")
			.contains("30 minutos");
		TestMailServer.purge();

		ResponseEntity<String> confirm = confirm(email.token(), NEW_PASSWORD);

		assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(login(api.central(), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertProblem(login(api.central(), TestFixtures.PASSWORD), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
		Email confirmation = TestMailServer.awaitEmail("ana@example.com", "Sua senha do bandcore foi alterada");
		assertThat(confirmation.body()).contains("http://app.localhost:3000/forgot-password");
		assertThat(output.getOut()).doesNotContain(email.token())
			.doesNotContain(NEW_PASSWORD)
			.doesNotContain("ana@example.com");
	}

	@Test
	void everyPreviousSessionStopsWorkingAfterAReset() {
		ApiClient band = api.forBand("alpha");
		ResponseEntity<String> bandLogin = login(band, TestFixtures.PASSWORD);
		ResponseEntity<String> centralLogin = login(api.central(), TestFixtures.PASSWORD);
		String bandAccess = JsonPath.read(bandLogin.getBody(), "$.accessToken");
		String centralAccess = JsonPath.read(centralLogin.getBody(), "$.accessToken");
		assertThat(me(band, bandAccess).getStatusCode()).isEqualTo(HttpStatus.OK);
		TestClockConfiguration.CLOCK.advance(Duration.ofSeconds(1));

		requestReset("ana@example.com");
		confirm(resetToken("ana@example.com"), NEW_PASSWORD);

		assertThat(me(band, bandAccess).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(me(api.central(), centralAccess).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(band, Cookies.refreshToken(bandLogin)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(api.central(), Cookies.refreshToken(centralLogin)).getStatusCode())
			.isEqualTo(HttpStatus.UNAUTHORIZED);

		String fresh = JsonPath.read(login(band, NEW_PASSWORD).getBody(), "$.accessToken");
		assertThat(me(band, fresh).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void aTokenWorksOnlyOnce() {
		requestReset("ana@example.com");
		String token = resetToken("ana@example.com");
		confirm(token, NEW_PASSWORD);

		assertProblem(confirm(token, "yet another passphrase"), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(login(api.central(), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void expiredAndUnknownTokensAreRejectedTheSameWay() {
		requestReset("ana@example.com");
		String token = resetToken("ana@example.com");
		TestClockConfiguration.CLOCK.advance(PasswordResetService.TOKEN_LIFETIME);

		assertProblem(confirm(token, NEW_PASSWORD), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertProblem(confirm("x".repeat(43), NEW_PASSWORD), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(login(api.central(), TestFixtures.PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void tokensExpireAfterThirtyMinutesAndAreStoredHashed() {
		requestReset("ana@example.com");
		String token = resetToken("ana@example.com");

		assertThat(OwnerSql.queryForString(
				"SELECT extract(epoch FROM expires_at - created_at)::int FROM bandcore.password_reset_tokens"))
			.isEqualTo("1800");
		assertThat(OwnerSql.queryForString(
				"SELECT count(*) FROM bandcore.password_reset_tokens WHERE token_hash = sha256(convert_to('%s', 'UTF8'))"
					.formatted(token)))
			.isEqualTo("1");
	}

	@Test
	void aNewRequestInvalidatesThePreviousLink() {
		requestReset("ana@example.com");
		String first = resetToken("ana@example.com");
		TestMailServer.purge();
		requestReset("ana@example.com");
		String second = resetToken("ana@example.com");

		assertProblem(confirm(first, NEW_PASSWORD), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(confirm(second, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void anUnknownEmailGetsTheSameResponseAndStillGeneratesAToken() {
		ResponseEntity<String> known = requestReset("ana@example.com");
		resetToken("ana@example.com");
		clearInvocations(generator);

		ResponseEntity<String> unknown = requestReset("nobody@example.com");
		TestMailServer.settle();

		assertThat(unknown.getStatusCode()).isEqualTo(known.getStatusCode());
		assertThat(unknown.getBody()).isEqualTo(known.getBody());
		verify(generator, times(1)).generate();
		assertThat(TestMailServer.emailsTo("nobody@example.com")).isEmpty();
		assertThat(OwnerSql.queryForString("SELECT count(*) FROM bandcore.password_reset_tokens")).isEqualTo("1");
	}

	@Test
	void theNewPasswordMustFollowThePolicyAndAFailedAttemptKeepsTheToken() {
		requestReset("ana@example.com");
		String token = resetToken("ana@example.com");

		assertProblem(confirm(token, "short"), HttpStatus.UNPROCESSABLE_CONTENT, "PASSWORD_TOO_SHORT");
		assertProblem(confirm(token, "password1234"), HttpStatus.UNPROCESSABLE_CONTENT, "PASSWORD_TOO_COMMON");
		assertThat(confirm(token, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void resettingAnUnverifiedAccountAlsoVerifiesTheEmail() {
		fixtures.user("bia@example.com", TestFixtures.PASSWORD, false);

		api.central().postJson("/api/auth/password-reset", "{\"email\": \"bia@example.com\"}");
		confirm(resetToken("bia@example.com"), NEW_PASSWORD);

		assertThat(OwnerSql.queryForString(
				"SELECT email_verified_at IS NOT NULL FROM bandcore.users WHERE email = 'bia@example.com'"))
			.isEqualTo("t");
	}

	@Test
	void disabledAccountsGetNoLink() {
		OwnerSql.global("UPDATE bandcore.users SET status = 'DISABLED'");

		assertThat(requestReset("ana@example.com").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		TestMailServer.settle();

		assertThat(TestMailServer.emailsTo("ana@example.com")).isEmpty();
	}

	@Test
	void resetIsOnlyOnTheCentralHost() {
		ResponseEntity<String> onBandHost = api.forBand("alpha")
			.postJson("/api/auth/password-reset", "{\"email\": \"ana@example.com\"}");

		assertThat(onBandHost.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void invalidInputIsRejected() {
		assertProblem(requestReset("not an email"), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(api.central().postJson("/api/auth/password-reset/confirm", "{\"password\": \"" + NEW_PASSWORD + "\"}"),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
	}

	private static String resetToken(String email) {
		return TestMailServer.awaitEmail(email, RESET_SUBJECT).token();
	}

	private ResponseEntity<String> requestReset(String email) {
		return api.central().postJson("/api/auth/password-reset", "{\"email\": \"%s\"}".formatted(email));
	}

	private ResponseEntity<String> confirm(String token, String password) {
		return api.central()
			.postJson("/api/auth/password-reset/confirm",
					"{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, password));
	}

	private static ResponseEntity<String> login(ApiClient client, String password) {
		return client.postJson("/api/auth/login",
				"{\"email\": \"ana@example.com\", \"password\": \"%s\"}".formatted(password));
	}

	private static ResponseEntity<String> refresh(ApiClient client, String refreshToken) {
		return client.postJson("/api/auth/refresh", "", headers -> Cookies.send(headers, refreshToken));
	}

	private static ResponseEntity<String> me(ApiClient client, String accessToken) {
		return client.get("/api/me", headers -> headers.setBearerAuth(accessToken));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}
