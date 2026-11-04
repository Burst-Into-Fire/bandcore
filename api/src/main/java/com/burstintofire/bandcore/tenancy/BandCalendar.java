package com.burstintofire.bandcore.tenancy;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Dates in the current band's time zone (RNF18): "today", "this month" and "overdue" depend on
 * where the band is, not on the server.
 */
public interface BandCalendar {

	ZoneId zone();

	/** Today in the band's time zone, from the injected clock. */
	LocalDate today();

}
