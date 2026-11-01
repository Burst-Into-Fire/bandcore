package com.burstintofire.bandcore.shared.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sets the PostgreSQL settings the row-level security policies read: {@code app.band_id} and
 * {@code app.user_id}. They are always set with {@code set_config(..., true)}, which is local
 * to the current transaction, so a pooled connection never carries a previous band.
 *
 * <p>The transaction manager calls this at the start of every transaction (see
 * {@link TenantAwareTransactionManager}). Rule: tenant data is only accessed inside
 * transactions; outside one, no band is set and the policies return nothing.
 */
@Component
public class RowLevelSecurity {

	static final String SET_CONTEXT = "SELECT set_config('app.band_id', ?, true), set_config('app.user_id', ?, true)";

	private final EntityManager entityManager;

	public RowLevelSecurity(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * Switches the band of the current transaction. Only for flows that create a band and
	 * must write its first rows in the same transaction; everything else gets its band from
	 * the request host.
	 */
	public void switchBand(UUID bandId, UUID userId) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("The band can only be switched inside a transaction");
		}
		entityManager.unwrap(Session.class).doWork(connection -> apply(connection, bandId, userId));
	}

	static void apply(Connection connection, UUID bandId, UUID userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
			statement.setString(1, bandId == null ? "" : bandId.toString());
			statement.setString(2, userId == null ? "" : userId.toString());
			statement.execute();
		}
	}

}
