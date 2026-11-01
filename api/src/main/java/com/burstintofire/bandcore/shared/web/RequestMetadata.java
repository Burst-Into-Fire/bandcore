package com.burstintofire.bandcore.shared.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.burstintofire.bandcore.shared.logging.RequestId;

/** Who made the current HTTP request, for audit and security records. */
@Component
public class RequestMetadata {

	public static final int MAX_USER_AGENT_LENGTH = 512;

	/**
	 * @param requestId the request correlation id (also in the logs)
	 * @param clientAddress the client's IP address (see {@link ClientAddresses})
	 * @param userAgent truncated to {@value #MAX_USER_AGENT_LENGTH} characters; null when absent
	 */
	public record Snapshot(String requestId, String clientAddress, String userAgent) {
	}

	private final ClientAddresses clients;

	public RequestMetadata(ClientAddresses clients) {
		this.clients = clients;
	}

	/** Empty outside an HTTP request (background jobs). */
	public Optional<Snapshot> current() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return Optional.empty();
		}
		HttpServletRequest request = attributes.getRequest();
		Object requestId = request.getAttribute(RequestId.ATTRIBUTE);
		return Optional.of(new Snapshot(requestId == null ? null : requestId.toString(), clients.clientOf(request),
				truncate(request.getHeader(HttpHeaders.USER_AGENT))));
	}

	private static String truncate(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return null;
		}
		return userAgent.length() <= MAX_USER_AGENT_LENGTH ? userAgent : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
	}

}
