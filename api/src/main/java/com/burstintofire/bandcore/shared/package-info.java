/**
 * Cross-cutting infrastructure used by every module: error handling, request id, clock,
 * security baseline and, later, value objects such as {@code Money}.
 *
 * <p>This module is open: other modules may use its subpackages. Keep it free of business
 * rules and never let it depend on another module.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package com.burstintofire.bandcore.shared;

import org.springframework.modulith.ApplicationModule;
