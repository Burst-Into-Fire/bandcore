/**
 * Append-only audit trail of relevant actions.
 *
 * <p>Only the types in this package are the module's API. Subpackages are internal to the
 * module and must not be used by other modules (verified by {@code ModularityTest}).
 */
@ApplicationModule(displayName = "Audit")
package com.burstintofire.bandcore.audit;

import org.springframework.modulith.ApplicationModule;
