package com.burstintofire.bandcore.identity.events;

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

import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.RequestMetadata;

/**
 * Records account security events in {@code security_events}. Each record is committed in its
 * own transaction, so a failed login is kept even though the login's transaction rolls back.
 * Never pass passwords, tokens or the email of an unknown account in {@code details}.
 */
@Component
public class SecurityEvents {

	private static final String INSERT = """
			INSERT INTO security_events (id, occurred_at, event_type, user_id, host, details, request_id, ip_address,
			                             user_agent)
			VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::inet, ?)
			""";

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final RequestMetadata requests;

	private final IdGenerator ids;

	private final Clock clock;

	SecurityEvents(JdbcTemplate jdbc, JsonMapper json, RequestMetadata requests, IdGenerator ids, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.requests = requests;
		this.ids = ids;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(SecurityEventType type, UUID userId) {
		record(type, userId, Map.of());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(SecurityEventType type, UUID userId, Map<String, ?> details) {
		Optional<RequestMetadata.Snapshot> request = requests.current();
		String host = TenantContextHolder.scope().map(TenantContextHolder.Scope::host).orElse(null);
		jdbc.update(INSERT, ids.newId(), Timestamp.from(clock.instant()), type.name(), userId, host,
				json.writeValueAsString(details), request.map(RequestMetadata.Snapshot::requestId).orElse(null),
				request.map(RequestMetadata.Snapshot::clientAddress).orElse(null),
				request.map(RequestMetadata.Snapshot::userAgent).orElse(null));
	}

}
