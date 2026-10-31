package com.burstintofire.bandcore.shared.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the platform is served. {@code app.{baseDomain}} is the central host and
 * {@code {slug}.{baseDomain}} a band host.
 *
 * @param baseDomain {@code bandcore.app} in production, {@code localhost} in development
 * @param publicScheme scheme of the URLs sent to users (emails, redirects)
 * @param publicPort port of those URLs, or null for the scheme's default
 * @param trustedProxies remote addresses whose {@code X-Forwarded-Host} header is honored;
 *        every other request is resolved from its {@code Host} header only
 */
@ConfigurationProperties("bandcore.platform")
public record PlatformProperties(String baseDomain, String publicScheme, Integer publicPort,
		List<String> trustedProxies) {

	public static final String CENTRAL_LABEL = "app";

	public PlatformProperties {
		baseDomain = baseDomain == null || baseDomain.isBlank() ? "localhost" : baseDomain.strip().toLowerCase(Locale.ROOT);
		publicScheme = publicScheme == null || publicScheme.isBlank() ? "https" : publicScheme.strip();
		trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
	}

	public String centralHost() {
		return CENTRAL_LABEL + "." + baseDomain;
	}

	public String bandHost(String slug) {
		return slug + "." + baseDomain;
	}

	public String centralUrl() {
		return url(centralHost());
	}

	public String bandUrl(String slug) {
		return url(bandHost(slug));
	}

	private String url(String host) {
		return publicScheme + "://" + host + (publicPort == null ? "" : ":" + publicPort);
	}

}
