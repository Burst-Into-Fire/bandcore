package com.burstintofire.bandcore.shared.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers on every API response, errors included. Runs first, so even responses
 * decided before Spring Security (unknown host, oversized body) carry them. The web container
 * (nginx) sets the same headers on the pages it serves. There is deliberately no CORS
 * configuration: the API is same-origin only (ADR-0005).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(HttpProperties.class)
class SecurityHeadersFilter extends OncePerRequestFilter {

	static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; object-src 'none'; "
			+ "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

	static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

	static final String HSTS = "max-age=31536000; includeSubDomains";

	private final HttpProperties properties;

	SecurityHeadersFilter(HttpProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
		response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
		// API responses carry session tokens and band data: never store them in any cache.
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		if (properties.hsts()) {
			response.setHeader("Strict-Transport-Security", HSTS);
		}
		chain.doFilter(request, response);
	}

}
