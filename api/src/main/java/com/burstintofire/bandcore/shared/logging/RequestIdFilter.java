package com.burstintofire.bandcore.shared.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before every other filter (including Spring Security), so every log line and every
 * response of a request carries the same request id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = RequestId.resolve(request.getHeader(RequestId.HEADER));
		request.setAttribute(RequestId.ATTRIBUTE, requestId);
		response.setHeader(RequestId.HEADER, requestId);
		MDC.put(RequestId.MDC_KEY, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(RequestId.MDC_KEY);
		}
	}

}
