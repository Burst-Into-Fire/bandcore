package com.burstintofire.bandcore.tenancy.host;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;

import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.EndpointRegistry;
import com.burstintofire.bandcore.shared.web.EndpointRegistry.Scope;
import com.burstintofire.bandcore.tenancy.band.BandDirectory;
import com.burstintofire.bandcore.tenancy.band.BandDirectory.BandSnapshot;

/**
 * Decides, before authentication, which band a request belongs to. The band comes only from
 * the {@code Host} header ({@code X-Forwarded-Host} only from a trusted proxy):
 *
 * <ul>
 * <li>unknown or malformed host → 404; suspended band → generic 403;</li>
 * <li>central-only endpoint on a band host, or band endpoint on the central host → 404;</li>
 * <li>otherwise the host and band are stored in {@link TenantContextHolder} for the request.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TenantResolutionFilter extends OncePerRequestFilter {

	static final String FORWARDED_HOST = "X-Forwarded-Host";

	private final PlatformProperties platform;

	private final BandDirectory bands;

	private final EndpointRegistry endpoints;

	TenantResolutionFilter(PlatformProperties platform, BandDirectory bands, EndpointRegistry endpoints) {
		this.platform = platform;
		this.bands = bands;
		this.endpoints = endpoints;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<HandlerMethod> handler = endpoints.handlerFor(request);
		Scope scope = handler.map(EndpointRegistry::scope).orElse(null);
		if (scope == Scope.PLATFORM) {
			chain.doFilter(request, response);
			return;
		}

		TenantContextHolder.Scope resolved;
		switch (HostResolver.resolve(hostOf(request), platform)) {
			case HostResolver.Central central -> {
				if (scope == Scope.BAND) {
					response.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				resolved = new TenantContextHolder.Scope(central.host(), null);
			}
			case HostResolver.Band band -> {
				Optional<BandSnapshot> found = bands.findBySlug(band.slug());
				if (found.isEmpty() || scope == Scope.CENTRAL) {
					response.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				if (!found.get().isActive()) {
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				resolved = new TenantContextHolder.Scope(band.host(),
						new TenantContext(found.get().id(), found.get().slug()));
			}
			case HostResolver.Unknown unknown -> {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		}

		TenantContextHolder.set(resolved);
		try {
			chain.doFilter(request, response);
		}
		finally {
			TenantContextHolder.clear();
		}
	}

	private String hostOf(HttpServletRequest request) {
		String forwarded = request.getHeader(FORWARDED_HOST);
		if (forwarded != null && platform.trustedProxies().contains(request.getRemoteAddr())) {
			String[] values = forwarded.split(",");
			return values[values.length - 1].strip();
		}
		return request.getHeader(HttpHeaders.HOST);
	}

}
