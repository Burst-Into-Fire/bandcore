package com.burstintofire.bandcore.finance.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class SettlementsIT extends IntegrationTest {

	UUID alpha;

	UUID anaMember;

	UUID biaMember;

	UUID caioMember;

	UUID category;

	ApiClient band;

	String admin;

	String bia;

	String caio;

	@BeforeEach
	void bandWithAnAdminAndTwoMembers() {
		alpha = fixtures.band("alpha");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		UUID biaUser = fixtures.verifiedUser("bia@example.com");
		UUID caioUser = fixtures.verifiedUser("caio@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.membership(alpha, biaUser, "MEMBER");
		fixtures.membership(alpha, caioUser, "MEMBER");
		anaMember = fixtures.memberOf(alpha, ana);
		biaMember = fixtures.memberOf(alpha, biaUser);
		caioMember = fixtures.memberOf(alpha, caioUser);
		category = fixtures.category(alpha, "Estúdio");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		bia = fixtures.login(band, "bia@example.com");
		caio = fixtures.login(band, "caio@example.com");
	}

	@Test
	void aSettlementMovesBothBalancesAndTheTotalStaysZero() {
		// Ana paid R$ 90,00 for the three: Bia and Caio owe R$ 30,00 each.
		band.postJson("/api/expenses", """
				{"description": "Estúdio", "categoryId": "%s", "currency": "BRL", "amountCents": 9000,
				 "expenseDate": "2026-11-05", "status": "PAID", "paidByMemberId": "%s"}""".formatted(category, anaMember),
				headers -> headers.setBearerAuth(admin));

		ResponseEntity<String> created = create(bia, biaMember, anaMember, 3000, "\"note\": \" Pix \"");

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = JsonPath.read(created.getBody(), "$.id");
		assertThat(created.getHeaders().getLocation()).hasToString("/api/settlements/" + id);
		assertThat(JsonPath.<String>read(created.getBody(), "$.note")).isEqualTo("Pix");
		// Without a date, it is today in the band's time zone (the test clock says 2026-01-15).
		assertThat(JsonPath.<String>read(created.getBody(), "$.paidOn")).isEqualTo("2026-01-15");
		assertThat(balanceOf(anaMember)).isEqualTo(3000);
		assertThat(balanceOf(biaMember)).isZero();
		assertThat(balanceOf(caioMember)).isEqualTo(-3000);
		String balances = band.get("/api/balances", headers -> headers.setBearerAuth(caio)).getBody();
		assertThat(JsonPath.<List<Integer>>read(balances, "$.currencies[0].members[*].balanceCents").stream()
			.mapToInt(Integer::intValue)
			.sum()).isZero();
		assertThat(JsonPath.<List<Integer>>read(balances,
				"$.currencies[0].members[?(@.memberId == '%s')].sentCents".formatted(biaMember)))
			.containsExactly(3000);
		assertThat(auditActions()).containsExactly("settlement.created");
	}

	@Test
	void fromAndToMustBeDifferentMembersOfTheBand() {
		ResponseEntity<String> same = create(bia, biaMember, biaMember, 100, null);
		assertThat(same.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(JsonPath.<String>read(same.getBody(), "$.code")).isEqualTo("SETTLEMENT_SAME_MEMBER");

		UUID stranger = fixtures.member(fixtures.band("other"), "Stranger");
		assertThat(create(bia, biaMember, stranger, 100, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(create(bia, stranger, biaMember, 100, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(create(bia, biaMember, anaMember, 0, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.settlements")).isEqualTo("0");
	}

	@Test
	void membersWhoLeftCanStillSettle() {
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id = '%s'".formatted(caioMember));

		assertThat(create(admin, caioMember, anaMember, 100, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void membersChangeSettlementsTheyRecordedOrAreAPartyTo() {
		// Recorded by Ana between Ana and Bia: Bia is a party, Caio is not.
		String id = JsonPath.read(create(admin, anaMember, biaMember, 1000, null).getBody(), "$.id");

		assertThat(JsonPath.<Boolean>read(get(bia, id), "$.access.edit")).isTrue();
		assertThat(JsonPath.<Boolean>read(get(caio, id), "$.access.edit")).isFalse();
		assertProblem(patch(caio, id, "{\"version\": 0, \"amountCents\": 1200}"), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		ResponseEntity<String> edited = patch(bia, id, "{\"version\": 0, \"amountCents\": 1200, \"note\": \"Pix\"}");
		assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<Integer>read(edited.getBody(), "$.version")).isEqualTo(1);
		// Recorded by Caio between Ana and Bia: Caio may change it.
		String byCaio = JsonPath.read(create(caio, anaMember, biaMember, 500, null).getBody(), "$.id");
		assertThat(patch(caio, byCaio, "{\"version\": 0, \"note\": \"x\"}").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertProblem(band.send(HttpMethod.DELETE, "/api/settlements/" + id, null, headers -> headers.setBearerAuth(caio)),
				HttpStatus.FORBIDDEN, "ACCESS_DENIED");
	}

	@Test
	void editsUseTheVersionAndAreAuditedWithADiff() {
		String id = JsonPath.read(create(bia, biaMember, anaMember, 1000, null).getBody(), "$.id");

		patch(bia, id, "{\"version\": 0, \"amountCents\": 1500}");
		assertProblem(patch(admin, id, "{\"version\": 0, \"amountCents\": 2000}"), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");
		assertProblem(patch(bia, id, "{\"version\": 1, \"toMemberId\": \"%s\"}".formatted(biaMember)),
				HttpStatus.UNPROCESSABLE_CONTENT, "SETTLEMENT_SAME_MEMBER");

		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'settlement.updated'"))
			.contains("\"amount_cents\": {\"to\": 1500, \"from\": 1000}");
	}

	@Test
	void deletedSettlementsLeaveListsAndBalancesButStayInTheDatabase() {
		String id = JsonPath.read(create(bia, biaMember, anaMember, 1000, null).getBody(), "$.id");

		assertThat(band.send(HttpMethod.DELETE, "/api/settlements/" + id + "?version=0", null,
				headers -> headers.setBearerAuth(bia)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(band.get("/api/settlements/" + id, headers -> headers.setBearerAuth(bia)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(JsonPath.<List<Object>>read(band.get("/api/balances", headers -> headers.setBearerAuth(bia))
			.getBody(), "$.currencies")).isEmpty();
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT count(*) FROM bandcore.settlements WHERE deleted_at IS NOT NULL")).isEqualTo("1");
		assertThat(auditActions()).containsExactlyInAnyOrder("settlement.created", "settlement.deleted");
		assertProblem(band.send(HttpMethod.DELETE, "/api/members/" + biaMember, null,
				headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT, "MEMBER_HAS_HISTORY");
	}

	@Test
	void listsFilterByMemberCurrencyAndMonth() {
		create(bia, biaMember, anaMember, 100, "\"paidOn\": \"2026-10-31\", \"note\": \"Out\"");
		create(bia, caioMember, anaMember, 200, "\"paidOn\": \"2026-11-02\", \"note\": \"Nov\"");
		create(bia, caioMember, biaMember, 300, "\"paidOn\": \"2026-11-03\", \"currency\": \"USD\", \"note\": \"Usd\"");

		assertThat(notes("")).containsExactly("Usd", "Nov", "Out");
		assertThat(notes("?memberId=" + biaMember)).containsExactly("Usd", "Out");
		assertThat(notes("?currency=USD")).containsExactly("Usd");
		assertThat(notes("?month=2026-11")).containsExactly("Usd", "Nov");
		assertThat(notes("?month=2026-11&currency=BRL&memberId=" + caioMember)).containsExactly("Nov");
		ResponseEntity<String> page = band.get("/api/settlements?size=2&page=1", headers -> headers.setBearerAuth(bia));
		assertThat(JsonPath.<Integer>read(page.getBody(), "$.totalItems")).isEqualTo(3);
		assertThat(JsonPath.<List<String>>read(page.getBody(), "$.items[*].note")).containsExactly("Out");
	}

	private ResponseEntity<String> create(String token, UUID from, UUID to, long cents, String extra) {
		String json = """
				{"fromMemberId": "%s", "toMemberId": "%s", "currency": "BRL", "amountCents": %d%s}"""
			.formatted(from, to, cents, extra == null ? "" : ", " + extra);
		// A later "currency" key wins in Jackson, so tests can override it through extra.
		return band.postJson("/api/settlements", json, headers -> headers.setBearerAuth(token));
	}

	private ResponseEntity<String> patch(String token, String id, String json) {
		return band.patchJson("/api/settlements/" + id, json, headers -> headers.setBearerAuth(token));
	}

	private String get(String token, String id) {
		return band.get("/api/settlements/" + id, headers -> headers.setBearerAuth(token)).getBody();
	}

	private List<String> notes(String query) {
		ResponseEntity<String> response = band.get("/api/settlements" + query, headers -> headers.setBearerAuth(bia));
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return JsonPath.read(response.getBody(), "$.items[*].note");
	}

	private int balanceOf(UUID member) {
		String body = band.get("/api/balances", headers -> headers.setBearerAuth(bia)).getBody();
		List<Integer> values = JsonPath.read(body,
				"$.currencies[0].members[?(@.memberId == '%s')].balanceCents".formatted(member));
		return values.getFirst();
	}

	private List<String> auditActions() {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY action) FROM bandcore.audit_events WHERE action LIKE 'settlement.%'");
		return actions == null ? List.of() : List.of(actions.split(","));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}
