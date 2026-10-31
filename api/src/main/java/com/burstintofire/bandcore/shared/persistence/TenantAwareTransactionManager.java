package com.burstintofire.bandcore.shared.persistence;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;

/**
 * Starts every transaction with the row-level security context of the current request or job:
 * the band from {@link TenantContextHolder} (resolved from the host, never from client input)
 * and the user from the authenticated principal. Without a band, both settings are empty and
 * tenant tables return no rows and reject writes.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

	public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
		super(entityManagerFactory);
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		super.doBegin(transaction, definition);
		UUID bandId = TenantContextHolder.current().map(TenantContext::bandId).orElse(null);
		UUID userId = CurrentUser.id().orElse(null);
		EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager
			.getResource(obtainEntityManagerFactory());
		EntityManager entityManager = holder.getEntityManager();
		entityManager.unwrap(Session.class).doWork(connection -> RowLevelSecurity.apply(connection, bandId, userId));
	}

}
