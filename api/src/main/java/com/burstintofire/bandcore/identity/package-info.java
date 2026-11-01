/**
 * User accounts, credentials, sessions and tokens.
 *
 * <p>Only the types in this package are the module's API. Subpackages are internal to the
 * module and must not be used by other modules (verified by {@code ModularityTest}).
 *
 * <p>Identity does not depend on tenancy: band access is asked through {@link
 * com.burstintofire.bandcore.identity.BandAccess}, which the tenancy module implements.
 */
@ApplicationModule(displayName = "Identity")
package com.burstintofire.bandcore.identity;

import org.springframework.modulith.ApplicationModule;
