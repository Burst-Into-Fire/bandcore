package com.burstintofire.bandcore.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param hsts send {@code Strict-Transport-Security}; on in production, where every host is
 *        HTTPS, off locally, where hosts are plain HTTP
 * @param maxRequestBody larger request bodies are refused with 413
 */
@ConfigurationProperties("bandcore.http")
public record HttpProperties(boolean hsts, DataSize maxRequestBody) {

	public HttpProperties {
		maxRequestBody = maxRequestBody == null ? DataSize.ofMegabytes(1) : maxRequestBody;
	}

}
