package com.burstintofire.bandcore.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The endpoint can be called without authentication. Say why in {@link #value()}; the
 * isolation harness lists every public endpoint, so this is reviewed like code.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface PublicEndpoint {

	/** Why this endpoint needs no authentication. */
	String value();

}
