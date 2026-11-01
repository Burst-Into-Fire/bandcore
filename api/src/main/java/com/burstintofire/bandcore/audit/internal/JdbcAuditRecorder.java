package com.burstintofire.bandcore.audit.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.web.RequestMetadata;

/**
 * Writes with plain SQL in the caller's transaction. The band id is read from the transaction's
 * row-level security context inside the INSERT itself, so a record can never land in another
 * band, and without a band context the insert fails.
 */
@Component
class JdbcAuditRecorder implements AuditRecorder {

	private static final String INSERT = """
			INSERT INTO audit_events (id, band_id, occurred_at, actor_user_id, action, entity_type, entity_id, changes,
			                          request_id, ip_address, user_agent)
			VALUES (?, NULLIF(current_setting('app.band_id', true), '')::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::inet, ?)
			""";

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final RequestMetadata requests;

	private final IdGenerator ids;

	private final Clock clock;

	JdbcAuditRecorder(JdbcTemplate jdbc, JsonMapper json, RequestMetadata requests, IdGenerator ids, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.requests = requests;
		this.ids = ids;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String action, String entityType, UUID entityId, Map<String, ?> changes) {
		Optional<RequestMetadata.Snapshot> request = requests.current();
		jdbc.update(INSERT, ids.newId(), Timestamp.from(clock.instant()), CurrentUser.id().orElse(null), action,
				entityType, entityId, json.writeValueAsString(changes == null ? Map.of() : changes),
				request.map(RequestMetadata.Snapshot::requestId).orElse(null),
				request.map(RequestMetadata.Snapshot::clientAddress).orElse(null),
				request.map(RequestMetadata.Snapshot::userAgent).orElse(null));
	}

}
