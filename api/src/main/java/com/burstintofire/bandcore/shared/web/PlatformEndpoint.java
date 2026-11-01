package com.burstintofire.bandcore.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The endpoint does not belong to any band and answers on every host, including unknown
 * ones, without resolving a tenant (for example the status probe). Never use it for anything
 * that reads band or account data.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface PlatformEndpoint {

}
