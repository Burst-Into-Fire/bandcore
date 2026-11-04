package com.burstintofire.bandcore.tenancy.band;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.tenancy.BandCalendar;

/** The current band's time zone, from the band directory's short cache. */
@Component
class BandTimeZone implements BandCalendar {

	private final BandDirectory directory;

	private final Clock clock;

	BandTimeZone(BandDirectory directory, Clock clock) {
		this.directory = directory;
		this.clock = clock;
	}

	@Override
	public ZoneId zone() {
		String slug = TenantContextHolder.require().slug();
		return directory.findBySlug(slug)
			.map(band -> ZoneId.of(band.timezone()))
			.orElseGet(() -> ZoneId.of(Band.DEFAULT_TIMEZONE));
	}

	@Override
	public LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), zone());
	}

}
