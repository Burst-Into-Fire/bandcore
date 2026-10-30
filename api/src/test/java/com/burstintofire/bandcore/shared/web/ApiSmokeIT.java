package com.burstintofire.bandcore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.shared.logging.RequestId;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;

class ApiSmokeIT extends IntegrationTest {

	@Test
	void statusIsPublic() {
		ResponseEntity<String> response = api.get("/api/status");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(JsonPath.<String>read(response.getBody(), "$.status")).isEqualTo("UP");
	}

	@Test
	void requestIdIsGeneratedWhenMissing() {
		ResponseEntity<String> response = api.get("/api/status");

		assertThat(response.getHeaders().getFirst(RequestId.HEADER)).isNotBlank();
	}

	@Test
	void unsafeRequestIdIsReplaced() {
		ResponseEntity<String> response = api.get("/api/status",
				headers -> headers.set(RequestId.HEADER, "bad id\"} injected"));

		assertThat(response.getHeaders().getFirst(RequestId.HEADER)).isNotBlank()
			.doesNotContain(" ")
			.doesNotContain("injected");
	}

	@Test
	void healthProbesAnswerOnTheManagementPortOnly() {
		ApiClient management = ApiClient.onPort(managementPort);

		assertThat(management.get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(management.get("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(management.get("/actuator/env").getStatusCode().is2xxSuccessful()).isFalse();
		assertThat(api.get("/api/actuator/health").getStatusCode().is2xxSuccessful()).isFalse();
		assertThat(api.get("/actuator/health").getStatusCode().is2xxSuccessful()).isFalse();
	}

	/**
	 * Also writes the document to target/openapi/openapi.json, which the web app generates its
	 * typed client from ({@code npm run api:generate}).
	 */
	@Test
	void openApiDocumentDescribesThePublicApi() throws IOException {
		ResponseEntity<String> response = api.get("/api/v3/api-docs");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String document = response.getBody();
		assertThat(JsonPath.<String>read(document, "$.servers[0].url")).isEqualTo("/api");
		assertThat(JsonPath.<Map<String, Object>>read(document, "$.paths")).containsKey("/status");

		Path output = Path.of("target", "openapi", "openapi.json");
		Files.createDirectories(output.getParent());
		Files.writeString(output, document, StandardCharsets.UTF_8);
	}

}
