package com.burstintofire.bandcore.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mock.env.MockEnvironment;

import com.burstintofire.bandcore.BandcoreApplication;

class ProductionConfigurationTest {

	@Test
	void reportsEveryMissingOrBlankSetting() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/x")
			.withProperty("spring.datasource.username", "bandcore_app")
			.withProperty("spring.datasource.password", " ")
			.withProperty("spring.flyway.user", "${DB_OWNER_USER}");

		assertThat(ProductionConfiguration.missing(environment)).containsExactly("DB_APP_PASSWORD", "DB_OWNER_USER",
				"DB_OWNER_PASSWORD", "BASE_DOMAIN");
	}

	@Test
	void acceptsCompleteConfiguration() {
		MockEnvironment environment = new MockEnvironment();
		ProductionConfiguration.REQUIRED.keySet().forEach(property -> environment.setProperty(property, "value"));

		assertThat(ProductionConfiguration.missing(environment)).isEmpty();
	}

	@Test
	void prodProfileFailsFastWithoutSecrets() {
		assumeTrue(ProductionConfiguration.REQUIRED.values().stream().allMatch(name -> System.getenv(name) == null),
				"DB_* variables are set in this shell");
		SpringApplicationBuilder application = new SpringApplicationBuilder(BandcoreApplication.class).profiles("prod")
			.web(WebApplicationType.NONE);

		Throwable failure = catchThrowable(application::run);

		assertThat(NestedExceptionUtils.getMostSpecificCause(failure)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("DB_URL")
			.hasMessageContaining("DB_APP_PASSWORD")
			.hasMessageContaining("DB_OWNER_PASSWORD");
	}

}
