/**
 * Bands (tenants), tenant resolution, members, memberships and roles.
 *
 * <p>Only the types in this package are the module's API. Subpackages are internal to the
 * module and must not be used by other modules (verified by {@code ModularityTest}).
 */
@ApplicationModule(displayName = "Tenancy")
package com.burstintofire.bandcore.tenancy;

import org.springframework.modulith.ApplicationModule;
