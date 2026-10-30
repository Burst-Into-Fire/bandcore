package com.burstintofire.bandcore.shared.persistence;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class PersistenceConfiguration {

	/** Replaces Spring Boot's JPA transaction manager (same bean name, same customizers). */
	@Bean
	PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
			ObjectProvider<TransactionManagerCustomizers> customizers) {
		TenantAwareTransactionManager transactionManager = new TenantAwareTransactionManager(entityManagerFactory);
		customizers.ifAvailable(each -> each.customize(transactionManager));
		return transactionManager;
	}

}
