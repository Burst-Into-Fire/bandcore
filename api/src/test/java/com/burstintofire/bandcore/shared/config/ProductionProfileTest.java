package com.burstintofire.bandcore.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Settings that must hold in staging and production (application-prod.yml). */
class ProductionProfileTest {

	private final Properties prod = load("application-prod.yml");

	@Test
	void swaggerUiIsOff() {
		assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
	}

	@Test
	void hstsIsOn() {
		assertThat(prod.getProperty("bandcore.http.hsts")).isEqualTo("true");
	}

	@Test
	void actuatorStaysOnTheManagementPort() {
		Properties base = load("application.yml");

		assertThat(base.getProperty("management.server.port")).isEqualTo("${MANAGEMENT_PORT:8081}");
		assertThat(base.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
		assertThat(prod.getProperty("management.server.port")).isNull();
	}

	private static Properties load(String name) {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource(name));
		return yaml.getObject();
	}

}
