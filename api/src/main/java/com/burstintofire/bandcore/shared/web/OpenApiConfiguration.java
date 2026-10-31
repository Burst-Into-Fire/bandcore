package com.burstintofire.bandcore.shared.web;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	/**
	 * A fixed, relative server URL keeps the generated document identical on every machine,
	 * which the frontend client drift check in CI depends on.
	 */
	@Bean
	OpenAPI bandcoreOpenApi() {
		return new OpenAPI().info(new Info().title("bandcore API").version("v1"))
			.servers(List.of(new Server().url("/api")));
	}

}
