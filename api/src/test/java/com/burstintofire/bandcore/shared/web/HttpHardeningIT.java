package com.burstintofire.bandcore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.shared.security.CsrfHeader;
import com.burstintofire.bandcore.support.IntegrationTest;

class HttpHardeningIT extends IntegrationTest {

	static final Map<String, String> SECURITY_HEADERS = Map.of(
			"Content-Security-Policy",
			"default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
			"X-Content-Type-Options", "nosniff",
			"X-Frame-Options", "DENY",
			"Referrer-Policy", "strict-origin-when-cross-origin",
			"Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
			"Cache-Control", "no-store");

	@Test
	void everyResponseCarriesTheSecurityHeaders() {
		assertSecurityHeaders(api.central().get("/api/status"), HttpStatus.OK);
		assertSecurityHeaders(api.central().get("/api/me"), HttpStatus.UNAUTHORIZED);
		assertSecurityHeaders(api.withHost("unknown.example.com").get("/api/me"), HttpStatus.NOT_FOUND);
		assertSecurityHeaders(api.central().postJson("/api/auth/login", "{}"), HttpStatus.BAD_REQUEST);
		assertSecurityHeaders(api.central().get("/api/does-not-exist"), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void hstsIsOffOnPlainHttp() {
		assertThat(api.central().get("/api/status").getHeaders().containsHeader("Strict-Transport-Security")).isFalse();
	}

	@Test
	void aCorsPreflightFromAnotherOriginIsNotAllowed() {
		ResponseEntity<String> preflight = api.central().send(HttpMethod.OPTIONS, "/api/auth/login", null, headers -> {
			headers.setOrigin("https://evil.example");
			headers.setAccessControlRequestMethod(HttpMethod.POST);
			headers.setAccessControlRequestHeaders(java.util.List.of("content-type", "x-requested-with"));
		});
		ResponseEntity<String> simple = api.central()
			.get("/api/status", headers -> headers.setOrigin("https://evil.example"));

		assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isNull();
		assertThat(preflight.getHeaders().getAccessControlAllowCredentials()).isFalse();
		assertThat(simple.getHeaders().getAccessControlAllowOrigin()).isNull();
	}

	@Test
	void anOversizedBodyIsRefused() throws Exception {
		HttpResponse<String> response = post("application/json",
				HttpRequest.BodyPublishers.ofString("{\"email\": \"" + "a".repeat(1_100_000) + "\"}"));

		assertProblem(response, 413, "PAYLOAD_TOO_LARGE");
	}

	@Test
	void anOversizedChunkedBodyIsRefusedWhileReading() throws Exception {
		byte[] body = ("{\"email\": \"" + "a".repeat(1_100_000) + "\"}").getBytes(StandardCharsets.UTF_8);
		HttpResponse<String> response = post("application/json",
				HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

		assertProblem(response, 413, "PAYLOAD_TOO_LARGE");
	}

	@Test
	void onlyJsonIsAcceptedOnJsonEndpoints() throws Exception {
		String json = "{\"email\": \"ana@example.com\", \"password\": \"a long and unique passphrase\"}";

		assertProblem(post("text/plain", HttpRequest.BodyPublishers.ofString(json)), 415, "UNSUPPORTED_MEDIA_TYPE");
		assertProblem(post("application/x-www-form-urlencoded",
				HttpRequest.BodyPublishers.ofString("email=ana%40example.com&password=x")), 415, "UNSUPPORTED_MEDIA_TYPE");
	}

	private HttpResponse<String> post(String contentType, HttpRequest.BodyPublisher body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
			.header(HttpHeaders.HOST, "app.localhost")
			.header(HttpHeaders.CONTENT_TYPE, contentType)
			.header(CsrfHeader.NAME, "XMLHttpRequest")
			.POST(body)
			.build();
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private static void assertProblem(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
		assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
	}

	private static void assertSecurityHeaders(ResponseEntity<String> response, HttpStatus status) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		SECURITY_HEADERS.forEach((name, value) -> assertThat(response.getHeaders().get(name))
			.as("%s on a %s response", name, status)
			.containsExactly(value));
	}

}
