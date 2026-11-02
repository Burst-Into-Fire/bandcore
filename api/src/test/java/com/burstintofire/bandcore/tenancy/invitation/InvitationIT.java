package com.burstintofire.bandcore.tenancy.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestFixtures;
import com.burstintofire.bandcore.support.TestMailServer;
import com.burstintofire.bandcore.support.TestMailServer.Email;

class InvitationIT extends IntegrationTest {

	static final String SUBJECT = "Convite para a banda The <Burst> no bandcore";

	UUID alpha;

	ApiClient band;

	String adminToken;

	@BeforeEach
	void adminOfAlpha() {
		alpha = fixtures.band("alpha", "The <Burst>");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		band = api.forBand("alpha");
		adminToken = fixtures.login(band, "ana@example.com");
	}

	@Test
	void anExistingAccountAcceptsAndLandsInTheBandWithTheRole() {
		UUID bia = fixtures.verifiedUser("bia@example.com");

		ResponseEntity<String> created = invite("BIA@example.com", "MEMBER", null);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<String>read(created.getBody(), "$.email")).isEqualTo("bia@example.com");
		assertThat(JsonPath.<String>read(created.getBody(), "$.status")).isEqualTo("PENDING");
		assertThat(created.getBody()).doesNotContain("token");
		Email email = TestMailServer.awaitEmail("bia@example.com", SUBJECT);
		assertThat(email.body()).contains("Ana convidou você para participar da banda \"The <Burst>\"")
			.contains("com o papel de integrante")
			.contains("http://app.localhost:3000/invitations/accept#token=");

		ResponseEntity<String> preview = api.central()
			.postJson("/api/invitations/preview", "{\"token\": \"%s\"}".formatted(email.token()));
		assertThat(JsonPath.<String>read(preview.getBody(), "$.bandName")).isEqualTo("The <Burst>");
		assertThat(JsonPath.<String>read(preview.getBody(), "$.email")).isEqualTo("bia@example.com");
		assertThat(JsonPath.<String>read(preview.getBody(), "$.role")).isEqualTo("MEMBER");

		ResponseEntity<String> accepted = accept("bia@example.com", email.token());

		assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(accepted.getBody(), "$.bandUrl")).isEqualTo("http://alpha.localhost:3000");
		String bandToken = fixtures.login(band, "bia@example.com");
		String me = band.get("/api/me", headers -> headers.setBearerAuth(bandToken)).getBody();
		assertThat(JsonPath.<String>read(me, "$.band.role")).isEqualTo("MEMBER");
		assertThat(memberNames()).containsExactly("Ana", "Bia");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT user_id::text FROM bandcore.members WHERE display_name = 'Bia'")).isEqualTo(bia.toString());
		assertThat(statusOf(JsonPath.read(created.getBody(), "$.id"))).isEqualTo("ACCEPTED");
		assertThat(auditActions()).contains("invitation.created", "membership.created", "member.created",
				"invitation.accepted");
	}

	@Test
	void aNewAccountSignsUpWithTheInvitedEmailAndAccepts() {
		invite("new@example.com", "READ_ONLY", null);
		String token = TestMailServer.awaitEmail("new@example.com", SUBJECT).token();
		api.central()
			.postJson("/api/auth/signup", "{\"email\": \"new@example.com\", \"password\": \"%s\", \"displayName\": \"Nina\"}"
				.formatted(TestFixtures.PASSWORD));
		ResponseEntity<String> unverified = accept("new@example.com", token);
		assertProblem(unverified, HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");

		String verification = TestMailServer.awaitEmail("new@example.com", "Confirme seu email no bandcore").token();
		api.central().postJson("/api/auth/verify-email", "{\"token\": \"%s\"}".formatted(verification));
		ResponseEntity<String> accepted = accept("new@example.com", token);

		assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(accepted.getBody(), "$.role")).isEqualTo("READ_ONLY");
		// A read-only manager gets access without becoming a member (integrante).
		assertThat(memberNames()).containsExactly("Ana");
	}

	@Test
	void anotherAccountCannotUseTheInvitation() {
		fixtures.verifiedUser("bia@example.com");
		fixtures.verifiedUser("carla@example.com");
		invite("bia@example.com", "ADMIN", null);
		String token = TestMailServer.awaitEmail("bia@example.com", SUBJECT).token();

		assertProblem(accept("carla@example.com", token), HttpStatus.FORBIDDEN, "INVITATION_EMAIL_MISMATCH");
		assertThat(accept("bia@example.com", token).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void usedRevokedAndExpiredTokensAreRejected() {
		fixtures.verifiedUser("bia@example.com");
		fixtures.verifiedUser("carla@example.com");
		fixtures.verifiedUser("davi@example.com");
		invite("bia@example.com", "MEMBER", null);
		String used = TestMailServer.awaitEmail("bia@example.com", SUBJECT).token();
		accept("bia@example.com", used);
		String revokedId = JsonPath.read(invite("carla@example.com", "MEMBER", null).getBody(), "$.id");
		String revoked = TestMailServer.awaitEmail("carla@example.com", SUBJECT).token();
		band.send(HttpMethod.DELETE, "/api/invitations/" + revokedId, null, this::admin);
		invite("davi@example.com", "MEMBER", null);
		String expired = TestMailServer.awaitEmail("davi@example.com", SUBJECT).token();
		TestClockConfiguration.CLOCK.advance(InvitationService.LIFETIME);
		adminToken = fixtures.login(band, "ana@example.com");

		assertProblem(accept("bia@example.com", used), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertProblem(accept("carla@example.com", revoked), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertProblem(accept("davi@example.com", expired), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertProblem(api.central().postJson("/api/invitations/preview", "{\"token\": \"%s\"}".formatted(expired)),
				HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(statusOf(revokedId)).isEqualTo("REVOKED");
	}

	@Test
	void sendingAgainGivesANewLinkAndKillsTheOldOne() {
		fixtures.verifiedUser("bia@example.com");
		String id = JsonPath.read(invite("bia@example.com", "MEMBER", null).getBody(), "$.id");
		String first = TestMailServer.awaitEmail("bia@example.com", SUBJECT).token();
		TestMailServer.purge();
		TestClockConfiguration.CLOCK.advance(Duration.ofDays(8));
		adminToken = fixtures.login(band, "ana@example.com");
		assertThat(statusOf(id)).isEqualTo("EXPIRED");

		ResponseEntity<String> resent = band.postJson("/api/invitations/" + id + "/resend", null, this::admin);

		assertThat(JsonPath.<String>read(resent.getBody(), "$.status")).isEqualTo("PENDING");
		String second = TestMailServer.awaitEmail("bia@example.com", SUBJECT).token();
		assertThat(second).isNotEqualTo(first);
		assertProblem(accept("bia@example.com", first), HttpStatus.BAD_REQUEST, "TOKEN_INVALID");
		assertThat(accept("bia@example.com", second).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(auditActions()).contains("invitation.resent");
	}

	@Test
	void anInvitationCanLinkAnExistingMember() {
		UUID bia = fixtures.member(alpha, "Bia (baixo)");
		fixtures.verifiedUser("bia@example.com");
		invite("bia@example.com", "MEMBER", bia);

		accept("bia@example.com", TestMailServer.awaitEmail("bia@example.com", SUBJECT).token());

		assertThat(memberNames()).containsExactly("Ana", "Bia (baixo)");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT user_id IS NOT NULL FROM bandcore.members WHERE id = '%s'".formatted(bia))).isEqualTo("t");
	}

	@Test
	void conflictsAndValidation() {
		fixtures.membership(alpha, fixtures.verifiedUser("bia@example.com"), "MEMBER");
		UUID linked = UUID.fromString(OwnerSql.superuserQueryForString(
				"SELECT id::text FROM bandcore.members WHERE display_name = 'Bia'"));

		assertProblem(invite("Bia@Example.com", "MEMBER", null), HttpStatus.CONFLICT, "ALREADY_A_MEMBER");
		assertProblem(invite("carla@example.com", "MEMBER", linked), HttpStatus.CONFLICT, "MEMBER_ALREADY_LINKED");
		assertProblem(invite("carla@example.com", "MEMBER", UUID.randomUUID()), HttpStatus.NOT_FOUND, "NOT_FOUND");
		invite("carla@example.com", "MEMBER", null);
		assertProblem(invite("carla@example.com", "ADMIN", null), HttpStatus.CONFLICT, "INVITATION_PENDING");
		assertProblem(invite("not-an-email", "MEMBER", null), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(band.postJson("/api/invitations", "{\"email\": \"x@example.com\", \"role\": \"OWNER\"}", this::admin),
				HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
	}

	@Test
	void invitationsArePerBandAndListedWithTheirStatus() {
		invite("bia@example.com", "MEMBER", null);
		UUID beta = fixtures.band("beta");
		fixtures.membership(beta, fixtures.verifiedUser("bruno@example.com"), "ADMIN");
		ApiClient betaHost = api.forBand("beta");
		String betaAdmin = fixtures.login(betaHost, "bruno@example.com");
		String alphaInvitation = JsonPath.<List<String>>read(list(), "$[*].id").getFirst();

		ResponseEntity<String> betaList = betaHost.get("/api/invitations", headers -> headers.setBearerAuth(betaAdmin));
		ResponseEntity<String> betaRevoke = betaHost.send(HttpMethod.DELETE, "/api/invitations/" + alphaInvitation, null,
				headers -> headers.setBearerAuth(betaAdmin));

		assertThat(JsonPath.<List<String>>read(betaList.getBody(), "$")).isEmpty();
		assertThat(betaRevoke.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(statusOf(alphaInvitation)).isEqualTo("PENDING");
	}

	@Test
	void acceptingIsOnlyOnTheCentralHost() {
		ResponseEntity<String> onBandHost = band.postJson("/api/invitations/accept", "{\"token\": \"x\"}", this::admin);

		assertThat(onBandHost.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void invitationsArePerBandPerDayRateLimited() {
		for (int i = 0; i < 30; i++) {
			assertThat(invite("guest" + i + "@example.com", "MEMBER", null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}
		ResponseEntity<String> limited = invite("one-more@example.com", "MEMBER", null);

		assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
	}

	private ResponseEntity<String> invite(String email, String role, UUID memberId) {
		String member = memberId == null ? "" : ", \"memberId\": \"%s\"".formatted(memberId);
		return band.postJson("/api/invitations", "{\"email\": \"%s\", \"role\": \"%s\"%s}".formatted(email, role, member),
				this::admin);
	}

	private ResponseEntity<String> accept(String email, String token) {
		String central = fixtures.login(api.central(), email);
		return api.central()
			.postJson("/api/invitations/accept", "{\"token\": \"%s\"}".formatted(token),
					headers -> headers.setBearerAuth(central));
	}

	private String list() {
		return band.get("/api/invitations", this::admin).getBody();
	}

	private String statusOf(String invitationId) {
		return JsonPath.<List<String>>read(list(), "$[?(@.id == '%s')].status".formatted(invitationId)).getFirst();
	}

	private void admin(HttpHeaders headers) {
		headers.setBearerAuth(adminToken);
	}

	private List<String> memberNames() {
		return List.of(OwnerSql
			.superuserQueryForString("SELECT string_agg(display_name, ',' ORDER BY display_name) FROM bandcore.members WHERE band_id = '%s'"
				.formatted(alpha))
			.split(","));
	}

	private static List<String> auditActions() {
		String actions = OwnerSql.superuserQueryForString("SELECT string_agg(action, ',') FROM bandcore.audit_events");
		return actions == null ? List.of() : List.of(actions.split(","));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}
