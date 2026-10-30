package com.burstintofire.bandcore.shared.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline: everything requires authentication unless listed here. The authentication
 * mechanism itself (JWT access token, refresh cookie) arrives in M2; until then every
 * non-public route answers 401 Problem Details.
 *
 * <p>Paths are relative to the {@code /api} context path.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(requests -> requests
			// Error dispatches render Problem Details for responses already decided upstream.
			.dispatcherTypeMatchers(DispatcherType.ERROR)
			.permitAll()
			// Health probes on the management port (never routed by the public proxy).
			.requestMatchers(EndpointRequest.to("health"))
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/status")
			.permitAll()
			// OpenAPI document; Swagger UI itself is disabled in production.
			.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
			.permitAll()
			.anyRequest()
			.authenticated())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// CSRF protection stays on (Spring Security's default). M2-07 configures it for the
			// refresh token cookie; bearer-token requests carry no ambient credentials.
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.exceptionHandling(exceptions -> exceptions
				// sendError hands the response to ProblemDetailsErrorController.
				.authenticationEntryPoint((request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
				.accessDeniedHandler((request, response, ex) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)));
		return http.build();
	}

}
