package com.burstintofire.bandcore.finance.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class ExpenseSplitsIT extends IntegrationTest {

	UUID alpha;

	UUID ana;

	UUID bia;

	UUID caio;

	UUID gear;

	/** Ana, Bia and Caio sorted by id as text: the order the extra cents follow. */
	List<UUID> byId;

	ApiClient band;

	String token;

	@BeforeEach
	void bandWithThreeMembers() {
		alpha = fixtures.band("alpha");
		UUID user = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, user, "ADMIN");
		ana = fixtures.memberOf(alpha, user);
		bia = fixtures.member(alpha, "Bia");
		caio = fixtures.member(alpha, "Caio");
		byId = Stream.of(ana, bia, caio).sorted(Comparator.comparing(UUID::toString)).toList();
		gear = fixtures.category(alpha, "Equipamento");
		band = api.forBand("alpha");
		token = fixtures.login(band, "ana@example.com");
	}

	/** RN01, whatever each test did. */
	@AfterEach
	void everyExpenseIsFullyShared() {
		assertThat(OwnerSql.superuserQueryForString("""
				SELECT count(*) FROM bandcore.expenses e
				WHERE e.amount_cents <> (SELECT coalesce(sum(s.share_cents), 0) FROM bandcore.expense_splits s
				                         WHERE s.band_id = e.band_id AND s.expense_id = e.id)
				""")).isEqualTo("0");
	}

	@Test
	void byDefaultEveryActiveMemberSharesEqually() {
		ResponseEntity<String> created = create(10_000, null);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<String>read(created.getBody(), "$.split.mode")).isEqualTo("EQUAL");
		assertThat(memberIds(created)).containsExactlyElementsOf(byId.stream().map(UUID::toString).toList());
		assertThat(shares(created)).containsExactly(3334, 3333, 3333);
	}

	@Test
	void equalSplitAmongChosenMembersAndThePayerNeedNotBeOne() {
		ResponseEntity<String> created = create(1001, """
				{"mode": "EQUAL", "shares": [{"memberId": "%s"}, {"memberId": "%s"}]}""".formatted(bia, caio));

		assertThat(JsonPath.<String>read(created.getBody(), "$.paidByMemberId")).isEqualTo(ana.toString());
		assertThat(memberIds(created)).doesNotContain(ana.toString()).hasSize(2);
		assertThat(shares(created)).containsExactly(501, 500);
	}

	@Test
	void customAmountsThatDoNotAddUpShowTheDifference() {
		ResponseEntity<String> response = create(10_000, """
				{"mode": "CUSTOM_AMOUNTS", "shares": [{"memberId": "%s", "amountCents": 6000},
				 {"memberId": "%s", "amountCents": 3000}]}""".formatted(ana, bia));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("SPLIT_SUM_MISMATCH");
		assertThat(JsonPath.<Integer>read(response.getBody(), "$.differenceCents")).isEqualTo(1000);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.expenses")).isEqualTo("0");

		ResponseEntity<String> ok = create(10_000, """
				{"mode": "CUSTOM_AMOUNTS", "shares": [{"memberId": "%s", "amountCents": 7000},
				 {"memberId": "%s", "amountCents": 3000}]}""".formatted(ana, bia));
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<List<Integer>>read(ok.getBody(), "$.split.shares[*].amountCents")).hasSize(2);
	}

	@Test
	void percentagesProduceExactCents() {
		ResponseEntity<String> created = create(100, """
				{"mode": "CUSTOM_PERCENTAGES", "shares": [{"memberId": "%s", "basisPoints": 3333},
				 {"memberId": "%s", "basisPoints": 3333}, {"memberId": "%s", "basisPoints": 3334}]}"""
			.formatted(byId.get(0), byId.get(1), byId.get(2)));

		assertThat(shares(created)).containsExactly(33, 33, 34);
		assertThat(JsonPath.<List<Integer>>read(created.getBody(), "$.split.shares[*].basisPoints"))
			.containsExactly(3333, 3333, 3334);

		ResponseEntity<String> wrong = create(100, """
				{"mode": "CUSTOM_PERCENTAGES", "shares": [{"memberId": "%s", "basisPoints": 6000}]}""".formatted(ana));
		assertThat(JsonPath.<String>read(wrong.getBody(), "$.code")).isEqualTo("SPLIT_PERCENT_SUM");
		assertThat(JsonPath.<Integer>read(wrong.getBody(), "$.differenceBasisPoints")).isEqualTo(4000);
	}

	@Test
	void editingTheAmountRecalculatesEqualAndPercentageSplits() {
		String equal = JsonPath.read(create(900, null).getBody(), "$.id");
		String percent = JsonPath.read(create(1000, """
				{"mode": "CUSTOM_PERCENTAGES", "shares": [{"memberId": "%s", "basisPoints": 2500},
				 {"memberId": "%s", "basisPoints": 7500}]}""".formatted(byId.get(0), byId.get(1))).getBody(), "$.id");
		String custom = JsonPath.read(create(1000, """
				{"mode": "CUSTOM_AMOUNTS", "shares": [{"memberId": "%s", "amountCents": 1000}]}""".formatted(ana))
			.getBody(), "$.id");

		assertThat(shares(patch(equal, "{\"version\": 0, \"amountCents\": 1000}"))).containsExactly(334, 333, 333);
		assertThat(shares(patch(percent, "{\"version\": 0, \"amountCents\": 1001}"))).containsExactly(250, 751);

		ResponseEntity<String> stale = patch(custom, "{\"version\": 0, \"amountCents\": 1200}");
		assertThat(JsonPath.<String>read(stale.getBody(), "$.code")).isEqualTo("SPLIT_SUM_MISMATCH");
		assertThat(JsonPath.<Integer>read(stale.getBody(), "$.differenceCents")).isEqualTo(200);
		ResponseEntity<String> resent = patch(custom, """
				{"version": 0, "amountCents": 1200, "split": {"mode": "CUSTOM_AMOUNTS",
				 "shares": [{"memberId": "%s", "amountCents": 1200}]}}""".formatted(ana));
		assertThat(shares(resent)).containsExactly(1200);
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'expense.updated' "
						+ "AND entity_id = '%s'".formatted(custom)))
			.contains("\"shares\"");
	}

	@Test
	void changingTheSplitModeReplacesTheShares() {
		String id = JsonPath.read(create(1000, null).getBody(), "$.id");

		ResponseEntity<String> changed = patch(id, """
				{"version": 0, "split": {"mode": "CUSTOM_AMOUNTS", "shares": [{"memberId": "%s", "amountCents": 400},
				 {"memberId": "%s", "amountCents": 600}]}}""".formatted(ana, bia));

		assertThat(JsonPath.<String>read(changed.getBody(), "$.split.mode")).isEqualTo("CUSTOM_AMOUNTS");
		assertThat(JsonPath.<List<Integer>>read(changed.getBody(), "$.split.shares[*].shareCents")).hasSize(2);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.expense_splits")).isEqualTo("2");
	}

	@Test
	void onlyActiveMembersOfTheBandJoinASplit() {
		UUID stranger = fixtures.member(fixtures.band("other"), "Stranger");
		assertThat(create(1000, """
				{"mode": "EQUAL", "shares": [{"memberId": "%s"}]}""".formatted(stranger)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);

		String id = JsonPath.read(create(1000, null).getBody(), "$.id");
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id = '%s'".formatted(caio));
		UUID dani = fixtures.member(alpha, "Dani");
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id = '%s'".formatted(dani));

		ResponseEntity<String> joining = create(1000, """
				{"mode": "EQUAL", "shares": [{"memberId": "%s"}, {"memberId": "%s"}]}""".formatted(ana, dani));
		assertThat(JsonPath.<String>read(joining.getBody(), "$.code")).isEqualTo("MEMBER_INACTIVE");
		// Caio left after the expense was split: editing it keeps him, recomputing his share.
		ResponseEntity<String> edited = patch(id, "{\"version\": 0, \"amountCents\": 1200}");
		assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(memberIds(edited)).contains(caio.toString());
		assertThat(shares(edited)).containsExactly(400, 400, 400);
		// And new equal splits without members leave him out.
		assertThat(memberIds(create(1000, null))).doesNotContain(caio.toString());
	}

	@Test
	void invalidSplitsAreRejected() {
		assertThat(JsonPath.<String>read(create(1000, "{\"mode\": \"CUSTOM_AMOUNTS\", \"shares\": []}").getBody(),
				"$.code")).isEqualTo("SPLIT_INVALID");
		assertThat(JsonPath.<String>read(create(1000, """
				{"mode": "EQUAL", "shares": [{"memberId": "%s"}, {"memberId": "%s"}]}""".formatted(ana, ana)).getBody(),
				"$.code")).isEqualTo("SPLIT_INVALID");
		assertThat(create(1000, """
				{"mode": "CUSTOM_PERCENTAGES", "shares": [{"memberId": "%s", "basisPoints": 10001}]}""".formatted(ana))
			.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void theDatabaseRefusesSharesThatDoNotAddUp() {
		String id = JsonPath.read(create(1000, null).getBody(), "$.id");

		assertThatThrownBy(() -> OwnerSql.inBand(alpha,
				"UPDATE bandcore.expense_splits SET share_cents = share_cents + 1 WHERE expense_id = '%s' AND member_id = '%s'"
					.formatted(id, ana)))
			.hasStackTraceContaining("adding up to 1001 cents instead of 1000");
		assertThatThrownBy(() -> OwnerSql.inBand(alpha,
				"UPDATE bandcore.expenses SET amount_cents = 999 WHERE id = '%s'".formatted(id)))
			.hasStackTraceContaining("instead of 999");
	}

	@Test
	void deletingAPlannedExpenseDeletesItsShares() {
		String id = JsonPath.read(create(1000, null, "PLANNED").getBody(), "$.id");

		assertThat(band.send(org.springframework.http.HttpMethod.DELETE, "/api/expenses/" + id, null,
				headers -> headers.setBearerAuth(token)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.expense_splits")).isEqualTo("0");
	}

	private ResponseEntity<String> create(long cents, String split) {
		return create(cents, split, "PAID");
	}

	private ResponseEntity<String> create(long cents, String split, String status) {
		String payer = status.equals("PAID") ? ", \"paidByMemberId\": \"%s\"".formatted(ana) : "";
		return band.postJson("/api/expenses", """
				{"description": "Van", "categoryId": "%s", "currency": "BRL", "amountCents": %d,
				 "expenseDate": "2026-11-10", "status": "%s"%s%s}""".formatted(gear, cents, status, payer,
				split == null ? "" : ", \"split\": " + split), headers -> headers.setBearerAuth(token));
	}

	private ResponseEntity<String> patch(String id, String json) {
		return band.patchJson("/api/expenses/" + id, json, headers -> headers.setBearerAuth(token));
	}

	private static List<Integer> shares(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).as(response.getBody()).isIn(HttpStatus.OK, HttpStatus.CREATED);
		return JsonPath.read(response.getBody(), "$.split.shares[*].shareCents");
	}

	private static List<String> memberIds(ResponseEntity<String> response) {
		return JsonPath.read(response.getBody(), "$.split.shares[*].memberId");
	}

}
