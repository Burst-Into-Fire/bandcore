package com.burstintofire.bandcore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.logging.RequestId;
import com.burstintofire.bandcore.support.IntegrationTest;

@ExtendWith(OutputCaptureExtension.class)
@Import(ProblemDetailsIT.TestEndpoints.class)
class ProblemDetailsIT extends IntegrationTest {

	private static final String SECRET_SQL = "SELECT password_hash FROM users";

	@Test
	void unknownRouteForAnonymousCallerIsUnauthorizedProblem() {
		ResponseEntity<String> response = api.get("/api/does-not-exist");

		assertProblem(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
	}

	@Test
	void unknownPublicRouteIsNotFoundProblem() {
		ResponseEntity<String> response = api.get("/api/test/does-not-exist");

		assertProblem(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
	}

	@Test
	void invalidBodyIsValidationProblemWithFieldErrors() {
		ResponseEntity<String> response = api.postJson("/api/test/validation", """
				{"name": " ", "quantity": 0}
				""");

		assertProblem(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		List<String> fields = JsonPath.read(response.getBody(), "$.errors[*].field");
		assertThat(fields).containsExactly("name", "quantity");
		List<String> codes = JsonPath.read(response.getBody(), "$.errors[*].code");
		assertThat(codes).containsExactly("NotBlank", "Positive");
	}

	@Test
	void malformedBodyIsProblemWithoutParserDetails() {
		ResponseEntity<String> response = api.postJson("/api/test/validation", "{\"name\": ");

		assertProblem(response, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
		assertThat(response.getBody()).doesNotContainIgnoringCase("jackson").doesNotContain("line: ");
	}

	@Test
	void unexpectedErrorIsGenericProblemAndIsLoggedWithRequestId(CapturedOutput output) {
		ResponseEntity<String> response = api.get("/api/test/failure", headers -> headers.set(RequestId.HEADER, "req-500"));

		assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
		assertThat(response.getBody()).doesNotContain(SECRET_SQL)
			.doesNotContain("IllegalStateException")
			.doesNotContain("at com.");
		assertThat(response.getHeaders().getFirst(RequestId.HEADER)).isEqualTo("req-500");
		assertThat(JsonPath.<String>read(response.getBody(), "$.requestId")).isEqualTo("req-500");
		assertThat(output.getOut()).contains("\"requestId\":\"req-500\"").contains(SECRET_SQL);
	}

	@Test
	void problemsCarryTheRequestId() {
		ResponseEntity<String> response = api.get("/api/does-not-exist", headers -> headers.set(RequestId.HEADER, "abc-123"));

		assertThat(response.getHeaders().getFirst(RequestId.HEADER)).isEqualTo("abc-123");
		assertThat(JsonPath.<String>read(response.getBody(), "$.requestId")).isEqualTo("abc-123");
	}

	@Test
	void bandHostIsSentToTheApi() {
		ResponseEntity<String> response = api.forBand("burst").get("/api/test/host");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("burst.localhost");
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType()).isNotNull()
			.satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
		String body = response.getBody();
		assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(status.value());
		assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo(status.getReasonPhrase());
		assertThat(JsonPath.<String>read(body, "$.code")).isEqualTo(code);
		assertThat(body).doesNotContain("trace").doesNotContain("exception");
	}

	/** Endpoints that exist only in this test, under a public path. */
	@TestConfiguration(proxyBeanMethods = false)
	@Import(TestController.class)
	static class TestEndpoints {

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		SecurityFilterChain testEndpointsSecurity(HttpSecurity http) throws Exception {
			return http.securityMatcher("/test/**")
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				// Test-only endpoints, called without a CSRF token.
				.csrf(csrf -> csrf.ignoringRequestMatchers("/test/**"))
				.build();
		}

	}

	@RestController
	static class TestController {

		@PostMapping("/test/validation")
		void validation(@Valid @RequestBody SampleRequest request) {
		}

		@GetMapping("/test/failure")
		void failure() {
			throw new IllegalStateException("Query failed: " + SECRET_SQL);
		}

		@GetMapping("/test/host")
		String host(@RequestHeader(HttpHeaders.HOST) String host) {
			return host;
		}

	}

	record SampleRequest(@NotBlank String name, @Positive int quantity) {
	}

}
