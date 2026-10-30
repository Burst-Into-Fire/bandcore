package com.burstintofire.bandcore.shared.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Fails the startup of the {@code prod} profile before any bean is created when a required
 * setting is missing or blank. Spring Boot leaves unresolved placeholders as literal text in
 * configuration properties, so without this check a missing secret would only show up later
 * as a confusing connection error.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProductionConfiguration {

	/** Property that must be set, mapped to the environment variable that sets it. */
	static final Map<String, String> REQUIRED = requiredSettings();

	@Bean
	static BeanFactoryPostProcessor requiredSettingsValidator(Environment environment) {
		return beanFactory -> {
			List<String> missing = missing(environment);
			if (!missing.isEmpty()) {
				throw new IllegalStateException(
						"Missing required configuration for the prod profile: " + String.join(", ", missing));
			}
		};
	}

	static List<String> missing(Environment environment) {
		List<String> missing = new ArrayList<>();
		REQUIRED.forEach((property, variable) -> {
			if (isBlank(environment, property)) {
				missing.add(variable);
			}
		});
		return missing;
	}

	private static boolean isBlank(Environment environment, String property) {
		try {
			String value = environment.getProperty(property);
			return value == null || value.isBlank();
		}
		catch (IllegalArgumentException unresolvedPlaceholder) {
			return true;
		}
	}

	private static Map<String, String> requiredSettings() {
		Map<String, String> settings = new LinkedHashMap<>();
		settings.put("spring.datasource.url", "DB_URL");
		settings.put("spring.datasource.username", "DB_APP_USER");
		settings.put("spring.datasource.password", "DB_APP_PASSWORD");
		settings.put("spring.flyway.user", "DB_OWNER_USER");
		settings.put("spring.flyway.password", "DB_OWNER_PASSWORD");
		settings.put("bandcore.platform.base-domain", "BASE_DOMAIN");
		settings.put("spring.mail.host", "SMTP_HOST");
		settings.put("bandcore.mail.from", "MAIL_FROM");
		return Collections.unmodifiableMap(settings);
	}

}
