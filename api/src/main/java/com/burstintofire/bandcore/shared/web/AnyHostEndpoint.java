package com.burstintofire.bandcore.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The endpoint exists on the central host and on band hosts (login, refresh, logout, current
 * user). On a band host the band is still resolved, and the session is scoped to it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface AnyHostEndpoint {

}
