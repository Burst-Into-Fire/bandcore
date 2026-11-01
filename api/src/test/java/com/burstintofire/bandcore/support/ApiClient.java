package com.burstintofire.bandcore.support;

import java.net.http.HttpClient;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.burstintofire.bandcore.shared.security.CsrfHeader;

/**
 * Real HTTP client for integration tests. Responses are returned as they are, without
 * throwing on 4xx or 5xx, so tests can assert on error bodies. Paths are relative to the
 * server root, so API paths start with {@code /api}.
 *
 * <p>Use {@link #forBand(String)} to call the API as a browser on {@code {slug}.localhost}
 * would, or {@link #central()} for {@code app.localhost}.
 */
public final class ApiClient {

	public static final String BASE_DOMAIN = "localhost";

	static {
		// Allows setting the Host header. Also set by Maven; this covers runs from the IDE.
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
	}

	private final RestClient client;

	private final String host;

	private final boolean csrfHeader;

	private ApiClient(RestClient client, String host, boolean csrfHeader) {
		this.client = client;
		this.host = host;
		this.csrfHeader = csrfHeader;
	}

	/** Client for the server on the given port, without a band host. */
	public static ApiClient onPort(int port) {
		RestClient client = RestClient.builder()
			.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
			.baseUrl("http://localhost:" + port)
			.defaultStatusHandler(status -> true, (request, response) -> {
			})
			.build();
		return new ApiClient(client, null, true);
	}

	/** Same client, sending {@code Host: {slug}.localhost}. */
	public ApiClient forBand(String slug) {
		return withHost(slug + "." + BASE_DOMAIN);
	}

	/** Same client, sending {@code Host: app.localhost}. */
	public ApiClient central() {
		return withHost("app." + BASE_DOMAIN);
	}

	public ApiClient withHost(String newHost) {
		return new ApiClient(client, newHost, csrfHeader);
	}

	/**
	 * Same client without the {@code X-Requested-With} header the web app always sends, to prove
	 * that cookie-based and anonymous unsafe requests need it.
	 */
	public ApiClient withoutCsrfHeader() {
		return new ApiClient(client, host, false);
	}

	public ResponseEntity<String> get(String path) {
		return get(path, headers -> {
		});
	}

	public ResponseEntity<String> get(String path, Consumer<HttpHeaders> headers) {
		return client.get().uri(path).headers(this::applyDefaults).headers(headers).retrieve().toEntity(String.class);
	}

	public ResponseEntity<String> postJson(String path, String json) {
		return postJson(path, json, headers -> {
		});
	}

	public ResponseEntity<String> postJson(String path, String json, Consumer<HttpHeaders> headers) {
		return send(HttpMethod.POST, path, json, headers);
	}

	public ResponseEntity<String> patchJson(String path, String json, Consumer<HttpHeaders> headers) {
		return send(HttpMethod.PATCH, path, json, headers);
	}

	/** Any method; {@code json} may be null for requests without a body. */
	public ResponseEntity<String> send(HttpMethod method, String path, String json, Consumer<HttpHeaders> headers) {
		RestClient.RequestBodySpec request = client.method(method).uri(path).headers(this::applyDefaults).headers(headers);
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).body(json);
		}
		return request.retrieve().toEntity(String.class);
	}

	private void applyHost(HttpHeaders headers) {
		if (host != null) {
			headers.set(HttpHeaders.HOST, host);
		}
	}

	private void applyDefaults(HttpHeaders headers) {
		applyHost(headers);
		if (csrfHeader) {
			headers.set(CsrfHeader.NAME, "XMLHttpRequest");
		}
	}

}
