package com.burstintofire.bandcore.security;

import java.sql.Connection;
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
	 * Every row of a band in every tenant table, plus the band itself, read as the superuser
	 * (which bypasses RLS). Compared before and after an attack to prove nothing changed.
	 */
	static List<String> snapshot(UUID bandId) {
		List<String> rows = new ArrayList<>();
		try (Connection connection = PostgresTestDatabase.superuserConnection();
				Statement statement = connection.createStatement()) {
			List<String> tables = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("""
					SELECT table_name FROM information_schema.columns
					WHERE table_schema = 'bandcore' AND column_name = 'band_id' AND table_name <> 'refresh_tokens'
					ORDER BY table_name
					""")) {
				while (rs.next()) {
					tables.add(rs.getString(1));
				}
			}
			for (String table : tables) {
				try (ResultSet rs = statement.executeQuery("SELECT row_to_json(t)::text FROM bandcore." + table
						+ " t WHERE band_id = '" + bandId + "' ORDER BY 1")) {
					while (rs.next()) {
						rows.add(table + " " + rs.getString(1));
					}
				}
			}
			try (ResultSet rs = statement
				.executeQuery("SELECT row_to_json(b)::text FROM bandcore.bands b WHERE id = '" + bandId + "'")) {
				while (rs.next()) {
					rows.add("bands " + rs.getString(1));
				}
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
		return rows;
	}

}
