package com.burstintofire.bandcore.shared.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}, used for side effects after a commit (emails), so slow providers
 * never hold a request or a transaction. Runs on Spring Boot's application task executor.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {

}
