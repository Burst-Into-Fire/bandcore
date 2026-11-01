package com.burstintofire.bandcore.identity.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.Cookies;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestFixtures;
import com.burstintofire.bandcore.support.TestMailServer;

class SecurityEventsIT extends IntegrationTest {

	static final String WRONG_PASSWORD = "not the right passphrase";

	UUID ana;

	@BeforeEach
	void ana() {
		UUID alpha = fixtures.band("alpha");
		ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "MEMBER");
	}

	@Test
	void aFailedLoginIsRecordedWithTheReasonButWithoutThePassword() {
		login(api.forBand("alpha"), "ana@example.com", WRONG_PASSWORD);

		String event = onlyEvent("LOGIN_FAILED");
		assertThat(JsonPath.<String>read(event, "$.user_id")).isEqualTo(ana.toString());
		assertThat(JsonPath.<String>read(event, "$.host")).isEqualTo("alpha.localhost");
		assertThat(JsonPath.<String>read(event, "$.details.reason")).isEqualTo("wrong_password");
		assertThat(JsonPath.<String>read(event, "$.ip_address")).isIn("127.0.0.1", "::1");
		assertThat(event).doesNotContain(WRONG_PASSWORD).doesNotContain(TestFixtures.PASSWORD);
	}

	@Test
	void aFailedLoginForAnUnknownEmailDoesNotStoreTheEmail() {
		login(api.central(), "nobody@example.com", WRONG_PASSWORD);

		String event = onlyEvent("LOGIN_FAILED");
		assertThat(JsonPath.<Object>read(event, "$.user_id")).isNull();
		assertThat(JsonPath.<String>read(event, "$.details.reason")).isEqualTo("unknown_email");
		assertThat(event).doesNotContain("nobody");
	}

	@Test
	void successfulLoginsAndLogoutEverywhereAreRecorded() {
		ResponseEntity<String> login = login(api.central(), "ana@example.com", TestFixtures.PASSWORD);
		String token = JsonPath.read(login.getBody(), "$.accessToken");

		api.central().postJson("/api/auth/logout-all", null, headers -> headers.setBearerAuth(token));

		assertThat(JsonPath.<String>read(onlyEvent("LOGIN_SUCCEEDED"), "$.host")).isEqualTo("app.localhost");
		assertThat(JsonPath.<String>read(onlyEvent("LOGOUT_ALL"), "$.user_id")).isEqualTo(ana.toString());
	}

	@Test
	void reusingARefreshTokenIsRecorded() {
		ApiClient band = api.forBand("alpha");
		String first = Cookies.refreshToken(login(band, "ana@example.com", TestFixtures.PASSWORD));
		refresh(band, first);

		refresh(band, first);

		String event = onlyEvent("REFRESH_TOKEN_REUSED");
		assertThat(JsonPath.<String>read(event, "$.user_id")).isEqualTo(ana.toString());
		assertThat(JsonPath.<String>read(event, "$.details.family_id")).isNotBlank();
	}

	@Test
	void rateLimitedLoginsAreRecorded() {
		for (int i = 0; i < 6; i++) {
			login(api.central(), "ana@example.com", WRONG_PASSWORD);
		}

		assertThat(count("LOGIN_FAILED")).isEqualTo(5);
		assertThat(count("LOGIN_RATE_LIMITED")).isEqualTo(1);
	}

	@Test
	void signupAndVerificationAreRecorded() {
		api.central()
			.postJson("/api/auth/signup",
					"{\"email\": \"bia@example.com\", \"password\": \"%s\", \"displayName\": \"Bia\"}"
						.formatted(TestFixtures.PASSWORD));
		String token = TestMailServer.awaitEmail("bia@example.com").token();
		api.central().postJson("/api/auth/verify-email", "{\"token\": \"%s\"}".formatted(token));

		assertThat(JsonPath.<String>read(onlyEvent("SIGNUP"), "$.details.outcome")).isEqualTo("created");
		assertThat(count("EMAIL_VERIFIED")).isEqualTo(1);
		assertThat(OwnerSql.queryForString("SELECT count(*) FROM bandcore.security_events WHERE details::text LIKE '%"
				+ token + "%' OR details::text LIKE '%bia@example.com%'"))
			.isEqualTo("0");
	}

	@Test
	void passwordResetsAreRecorded() {
		api.central().postJson("/api/auth/password-reset", "{\"email\": \"ana@example.com\"}");
		String token = TestMailServer.awaitEmail("ana@example.com").token();
		api.central()
			.postJson("/api/auth/password-reset/confirm",
					"{\"token\": \"%s\", \"password\": \"a brand new passphrase\"}".formatted(token));

		assertThat(JsonPath.<String>read(onlyEvent("PASSWORD_RESET_REQUESTED"), "$.user_id")).isEqualTo(ana.toString());
		assertThat(JsonPath.<String>read(onlyEvent("PASSWORD_RESET_COMPLETED"), "$.user_id"))
			.isEqualTo(ana.toString());
		assertThat(count("LOGOUT_ALL")).isZero();
	}

	private static String onlyEvent(String type) {
		assertThat(count(type)).as(type).isEqualTo(1);
		return OwnerSql.queryForString("""
				SELECT json_build_object('user_id', user_id, 'host', host, 'details', details,
				    'ip_address', host(ip_address), 'request_id', request_id, 'user_agent', user_agent)::text
				FROM bandcore.security_events WHERE event_type = '%s'
				""".formatted(type));
	}

	private static int count(String type) {
		return Integer.parseInt(OwnerSql
			.queryForString("SELECT count(*) FROM bandcore.security_events WHERE event_type = '%s'".formatted(type)));
	}

	private static ResponseEntity<String> login(ApiClient client, String email, String password) {
		return client.postJson("/api/auth/login",
				"{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
	}

	private static void refresh(ApiClient client, String token) {
		client.postJson("/api/auth/refresh", null, headers -> Cookies.send(headers, token));
	}

}
