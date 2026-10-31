package com.burstintofire.bandcore.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The endpoint exists only on the central host ({@code app.{BASE_DOMAIN}}): sign up, band
 * creation, "my bands". On a band host it answers 404.
 *
 * <p>Endpoints without a host annotation exist only on band hosts.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface CentralEndpoint {

}
