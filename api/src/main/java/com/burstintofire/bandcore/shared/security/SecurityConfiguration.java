package com.burstintofire.bandcore.shared.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import com.burstintofire.bandcore.shared.web.EndpointRegistry;

/**
 * Stateless API security. Everything requires a valid bearer access token unless the endpoint
 * is {@code @PublicEndpoint} or {@code @PlatformEndpoint}. The token decoder and the converter
 * that validates host, band and membership are provided by the identity module.
 *
 * <p>Paths are relative to the {@code /api} context path.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, EndpointRegistry endpoints, JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> accessTokenAuthenticator) throws Exception {
		AuthenticationEntryPoint unauthorized = (request, response, ex) -> {
			response.setHeader("WWW-Authenticate", "Bearer");
			// sendError hands the response to ProblemDetailsErrorController.
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		};
		http.authorizeHttpRequests(requests -> requests
			// Error dispatches render Problem Details for responses already decided upstream.
			.dispatcherTypeMatchers(DispatcherType.ERROR)
			.permitAll()
			// Health probes on the management port (never routed by the public proxy).
			.requestMatchers(EndpointRequest.to("health"))
			.permitAll()
			// @PublicEndpoint and @PlatformEndpoint controllers (see EndpointRegistry).
			.requestMatchers(endpoints::isPublic)
			.permitAll()
			// OpenAPI document; Swagger UI itself is disabled in production.
			.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
			.permitAll()
			.anyRequest()
			.authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.bearerTokenResolver(ignoredOnPublicEndpoints(endpoints))
				.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(accessTokenAuthenticator))
				.authenticationEntryPoint(unauthorized))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Unsafe requests without a bearer token must send X-Requested-With (see CsrfHeader).
			.csrf(csrf -> csrf.requireCsrfProtectionMatcher(CsrfHeader::requiresProtection)
				.csrfTokenRepository(new CsrfHeader.NoTokenRepository()))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorized)
				.accessDeniedHandler((request, response, ex) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)));
		return http.build();
	}

	/**
	 * Public endpoints (login, refresh, logout) never look at the Authorization header, so a stale
	 * access token can never block them.
	 */
	private static BearerTokenResolver ignoredOnPublicEndpoints(EndpointRegistry endpoints) {
		DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
		return request -> endpoints.isPublic(request) ? null : resolver.resolve(request);
	}

}
