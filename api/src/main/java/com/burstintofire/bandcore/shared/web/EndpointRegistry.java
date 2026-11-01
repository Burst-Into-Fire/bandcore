package com.burstintofire.bandcore.shared.web;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Finds the controller method a request is routed to, before the request reaches Spring MVC,
 * so the tenant filter and Spring Security can apply the endpoint's host scope and public
 * flag. Also lists every endpoint for the authorization and isolation tests.
 */
@Component
public class EndpointRegistry {

	/** Where the host scope applies; framework endpoints (OpenAPI) are platform endpoints. */
	private static final String APPLICATION_PACKAGE = "com.burstintofire.bandcore";

	private static final String ATTRIBUTE = EndpointRegistry.class.getName() + ".handler";

	private static final Object NO_HANDLER = new Object();

	public enum Scope {

		/** Band hosts only (default). */
		BAND,
		/** Central host only. */
		CENTRAL,
		/** Central host and band hosts. */
		ANY_HOST,
		/** Every host, without a tenant. */
		PLATFORM

	}

	private final RequestMappingHandlerMapping mapping;

	public EndpointRegistry(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
		this.mapping = mapping;
	}

	/** The controller method for this request, or empty for unknown routes. Cached per request. */
	public Optional<HandlerMethod> handlerFor(HttpServletRequest request) {
		Object cached = request.getAttribute(ATTRIBUTE);
		if (cached == null) {
			cached = lookup(request);
			request.setAttribute(ATTRIBUTE, cached);
		}
		return cached instanceof HandlerMethod method ? Optional.of(method) : Optional.empty();
	}

	public boolean isPublic(HttpServletRequest request) {
		return handlerFor(request).map(EndpointRegistry::isPublic).orElse(false);
	}

	public static boolean isPublic(HandlerMethod handler) {
		return find(handler, PublicEndpoint.class) != null || scope(handler) == Scope.PLATFORM;
	}

	public static Scope scope(HandlerMethod handler) {
		if (!handler.getBeanType().getName().startsWith(APPLICATION_PACKAGE)) {
			return Scope.PLATFORM;
		}
		Scope onMethod = declaredScope(handler.getMethod());
		if (onMethod != null) {
			return onMethod;
		}
		Scope onClass = declaredScope(handler.getBeanType());
		return onClass != null ? onClass : Scope.BAND;
	}

	private static Scope declaredScope(AnnotatedElement element) {
		if (AnnotatedElementUtils.hasAnnotation(element, PlatformEndpoint.class)) {
			return Scope.PLATFORM;
		}
		if (AnnotatedElementUtils.hasAnnotation(element, CentralEndpoint.class)) {
			return Scope.CENTRAL;
		}
		if (AnnotatedElementUtils.hasAnnotation(element, AnyHostEndpoint.class)) {
			return Scope.ANY_HOST;
		}
		return null;
	}

	/** Every application endpoint, for the security tests. The error controller is excluded. */
	public Map<RequestMappingInfo, HandlerMethod> applicationEndpoints() {
		Map<RequestMappingInfo, HandlerMethod> endpoints = new java.util.LinkedHashMap<>();
		mapping.getHandlerMethods().forEach((info, handler) -> {
			if (handler.getBeanType().getName().startsWith(APPLICATION_PACKAGE)
					&& !ErrorController.class.isAssignableFrom(handler.getBeanType())) {
				endpoints.put(info, handler);
			}
		});
		return endpoints;
	}

	/** Method annotations win over class annotations. */
	public static <A extends Annotation> A find(HandlerMethod handler, Class<A> type) {
		A onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), type);
		return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), type);
	}

	private Object lookup(HttpServletRequest request) {
		boolean parsedHere = !ServletRequestPathUtils.hasParsedRequestPath(request);
		if (parsedHere) {
			ServletRequestPathUtils.parseAndCache(request);
		}
		try {
			HandlerExecutionChain chain = mapping.getHandler(request);
			return chain != null && chain.getHandler() instanceof HandlerMethod method ? method : NO_HANDLER;
		}
		catch (Exception noMatch) {
			// Method not allowed, unsupported media type...: Spring MVC answers those itself.
			return NO_HANDLER;
		}
		finally {
			if (parsedHere) {
				ServletRequestPathUtils.clearParsedRequestPath(request);
			}
		}
	}

}
