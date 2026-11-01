package com.burstintofire.bandcore.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.burstintofire.bandcore.shared.web.EndpointRegistry;
import com.burstintofire.bandcore.shared.web.PublicEndpoint;
import com.burstintofire.bandcore.support.PostgresTestDatabase;

/** Endpoint enumeration and data snapshots used by the isolation tests. */
final class IsolationHarness {

	private IsolationHarness() {
	}

	/**
	 * Endpoints that need isolation scenarios but have none. Public, central and platform
	 * endpoints are exempt: they have no band data behind an authenticated band session.
	 */
	static Set<String> unregistered(Map<RequestMappingInfo, HandlerMethod> endpoints, List<IsolationScenarios.Probe> probes) {
		Set<String> registered = new TreeSet<>();
		probes.forEach(probe -> registered.add(probe.key()));
		Set<String> missing = new TreeSet<>();
		endpoints.forEach((info, handler) -> {
			EndpointRegistry.Scope scope = EndpointRegistry.scope(handler);
			if (EndpointRegistry.isPublic(handler) || scope == EndpointRegistry.Scope.CENTRAL
					|| scope == EndpointRegistry.Scope.PLATFORM) {
				return;
			}
			for (String key : AuthorizationMatrixIT.keys(info, handler)) {
				if (!registered.contains(key)) {
					missing.add(key);
				}
			}
		});
		return missing;
	}

	/** Public endpoints whose reason is missing: every exemption must be explained. */
	static Set<String> publicWithoutReason(Map<RequestMappingInfo, HandlerMethod> endpoints) {
		Set<String> missing = new TreeSet<>();
		endpoints.forEach((info, handler) -> {
			PublicEndpoint annotation = EndpointRegistry.find(handler, PublicEndpoint.class);
			if (annotation != null && annotation.value().isBlank()) {
				missing.addAll(AuthorizationMatrixIT.keys(info, handler));
			}
		});
		return missing;
	}

	/**
	 * Builds the snapshot inside PostgreSQL, so no SQL is assembled in Java: table names are
	 * quoted with {@code format('%I')} and the band id is a bound parameter. The function lives
	 * in {@code pg_temp}, so it disappears when the connection closes.
	 */
	private static final String SNAPSHOT_FUNCTION = """
			CREATE FUNCTION pg_temp.band_snapshot(band uuid) RETURNS SETOF text
			LANGUAGE plpgsql AS $$
			DECLARE
				tbl text;
			BEGIN
				FOR tbl IN
					SELECT table_name FROM information_schema.columns
					WHERE table_schema = 'bandcore' AND column_name = 'band_id' AND table_name <> 'refresh_tokens'
				LOOP
					RETURN QUERY EXECUTE format(
						'SELECT %L || '' '' || row_to_json(t)::text FROM bandcore.%I t WHERE band_id = $1', tbl, tbl)
						USING band;
				END LOOP;
				RETURN QUERY SELECT 'bands ' || row_to_json(b)::text FROM bandcore.bands b WHERE id = band;
			END
			$$
			""";

	/**
	 * Every row of a band in every tenant table, plus the band itself, read as the superuser
	 * (which bypasses RLS). Compared before and after an attack to prove nothing changed.
	 */
	static List<String> snapshot(UUID bandId) {
		List<String> rows = new ArrayList<>();
		try (Connection connection = PostgresTestDatabase.superuserConnection()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute(SNAPSHOT_FUNCTION);
			}
			try (PreparedStatement query = connection
				.prepareStatement("SELECT r FROM pg_temp.band_snapshot(?) AS r ORDER BY 1")) {
				query.setObject(1, bandId);
				try (ResultSet rs = query.executeQuery()) {
					while (rs.next()) {
						rows.add(rs.getString(1));
					}
				}
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
		return rows;
	}

}
