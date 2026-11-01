package com.burstintofire.bandcore.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class AuditLogIT extends IntegrationTest {

	@Autowired
	AuditRecorder audit;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	UUID alpha;

	UUID beta;

	UUID ana;

	@BeforeEach
	void bandsWithAdmins() {
		alpha = fixtures.band("alpha");
		beta = fixtures.band("beta");
		ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.membership(beta, fixtures.verifiedUser("bia@example.com"), "ADMIN");
	}

	@ParameterizedTest
	@ValueSource(strings = { "UPDATE bandcore.audit_events SET action = 'band.deleted'",
			"DELETE FROM bandcore.audit_events", "TRUNCATE bandcore.audit_events",
			"UPDATE bandcore.security_events SET event_type = 'SIGNUP'", "DELETE FROM bandcore.security_events",
			"TRUNCATE bandcore.security_events" })
	void theApplicationCannotChangeOrDeleteAuditRecords(String sql) {
		assertThatThrownBy(() -> inBand(alpha, () -> jdbc.update(sql))).rootCause()
			.hasMessageContaining("permission denied");
	}

	@Test
	void aBandChangeRecordsWhoWhatAndWhenWithTheRequest() {
		ApiClient band = api.forBand("alpha");
		String token = fixtures.login(band, "ana@example.com");

		ResponseEntity<String> response = band.patchJson("/api/band", "{\"name\": \"Renamed\"}", headers -> {
			headers.setBearerAuth(token);
			headers.set("User-Agent", "AuditTest/1.0");
		});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String row = OwnerSql.superuserQueryForString("""
				SELECT json_build_object('band', band_id, 'actor', actor_user_id, 'action', action,
				    'entity_type', entity_type, 'entity', entity_id, 'changes', changes, 'request_id', request_id,
				    'ip', host(ip_address), 'user_agent', user_agent, 'at', occurred_at)::text
				FROM bandcore.audit_events WHERE action = 'band.updated'
				""");
		assertThat(JsonPath.<String>read(row, "$.band")).isEqualTo(alpha.toString());
		assertThat(JsonPath.<String>read(row, "$.actor")).isEqualTo(ana.toString());
		assertThat(JsonPath.<String>read(row, "$.entity_type")).isEqualTo("band");
		assertThat(JsonPath.<String>read(row, "$.entity")).isEqualTo(alpha.toString());
		assertThat(JsonPath.<Map<String, Object>>read(row, "$.changes"))
			.isEqualTo(Map.of("name", Map.of("from", "Alpha", "to", "Renamed")));
		assertThat(JsonPath.<String>read(row, "$.request_id")).isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
		assertThat(JsonPath.<String>read(row, "$.ip")).isIn("127.0.0.1", "::1");
		assertThat(JsonPath.<String>read(row, "$.user_agent")).isEqualTo("AuditTest/1.0");
		assertThat(JsonPath.<String>read(row, "$.at")).startsWith("2026-01-15T15:00:00");
	}

	@Test
	void aRequestThatChangesNothingRecordsNothing() {
		ApiClient band = api.forBand("alpha");
		String token = fixtures.login(band, "ana@example.com");

		band.patchJson("/api/band", "{\"name\": \"Alpha\"}", headers -> headers.setBearerAuth(token));

		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.audit_events")).isEqualTo("0");
	}

	@Test
	void creatingABandRecordsTheBandAndTheCreatorsMembership() {
		ApiClient central = api.central();
		String token = fixtures.login(central, "ana@example.com");

		ResponseEntity<String> created = central.postJson("/api/bands", "{\"name\": \"New\", \"slug\": \"brand-new\"}",
				headers -> headers.setBearerAuth(token));

		String bandId = JsonPath.read(created.getBody(), "$.id");
		assertThat(OwnerSql.superuserQueryForString("""
				SELECT string_agg(action || ' ' || actor_user_id, ', ' ORDER BY action) FROM bandcore.audit_events
				WHERE band_id = '%s'
				""".formatted(bandId))).isEqualTo("band.created " + ana + ", membership.created " + ana);
	}

	@Test
	void aFailedChangeRollsBackItsAuditRecord() {
		assertThatThrownBy(() -> inBand(alpha, () -> {
			audit.record("band.updated", "band", alpha, Map.of("name", AuditRecorder.change("Alpha", "Lost")));
			throw new IllegalStateException("business rule failed");
		})).hasMessage("business rule failed");

		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.audit_events")).isEqualTo("0");
	}

	@Test
	void recordingNeedsTheBusinessTransaction() {
		assertThatThrownBy(() -> TenantContextHolder.callAs(new TenantContext(alpha, "alpha"),
				() -> {
					audit.record("band.updated", "band", alpha, Map.of());
					return null;
				}))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void auditRecordsAreIsolatedBetweenBands() {
		inBand(alpha, () -> audit.record("band.updated", "band", alpha, Map.of()));
		inBand(beta, () -> audit.record("band.updated", "band", beta, Map.of()));

		Long seenFromAlpha = inBand(alpha,
				() -> jdbc.queryForObject("SELECT count(*) FROM bandcore.audit_events", Long.class));
		String bandOfAlphaRow = inBand(alpha,
				() -> jdbc.queryForObject("SELECT band_id::text FROM bandcore.audit_events", String.class));

		assertThat(seenFromAlpha).isEqualTo(1);
		assertThat(bandOfAlphaRow).isEqualTo(alpha.toString());
		assertThatThrownBy(() -> inBand(alpha, () -> jdbc.update("""
				INSERT INTO bandcore.audit_events (id, band_id, occurred_at, action, entity_type)
				VALUES (gen_random_uuid(), ?, now(), 'band.updated', 'band')
				""", beta))).rootCause().hasMessageContaining("row-level security");
	}

	private <T> T inBand(UUID bandId, java.util.function.Supplier<T> action) {
		return TenantContextHolder.callAs(new TenantContext(bandId, "band"), () -> transactions.execute(status -> action.get()));
	}

	private void inBand(UUID bandId, Runnable action) {
		inBand(bandId, () -> {
			action.run();
			return null;
		});
	}

}
