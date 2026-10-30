package com.burstintofire.bandcore.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Test setup straight into the database as the schema owner. Row-level security is forced, so
 * it applies to the owner too: tenant rows are written with {@code app.band_id} set to their
 * band, exactly like the application does.
 */
public final class OwnerSql {

	private OwnerSql() {
	}

	/** Runs statements as the owner, with no band (global tables only). */
	public static void global(String... statements) {
		inBand(null, statements);
	}

	/** Runs statements as the owner inside one band's RLS context. */
	public static void inBand(UUID bandId, String... statements) {
		try (Connection connection = PostgresTestDatabase.ownerConnection()) {
			try (PreparedStatement context = connection
				.prepareStatement("SELECT set_config('app.band_id', ?, false)")) {
				context.setString(1, bandId == null ? "" : bandId.toString());
				context.execute();
			}
			try (Statement statement = connection.createStatement()) {
				for (String sql : statements) {
					statement.execute(sql);
				}
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Test setup failed", ex);
		}
	}

}
