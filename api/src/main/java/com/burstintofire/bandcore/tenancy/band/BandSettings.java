package com.burstintofire.bandcore.tenancy.band;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * The current band's own data. The band is always the one of the request host; there is no
 * way to address another band here. The settings page and audit arrive in M4-04.
 */
@Service
public class BandSettings {

	private final BandRepository bands;

	private final BandDirectory directory;

	private final Clock clock;

	BandSettings(BandRepository bands, BandDirectory directory, Clock clock) {
		this.bands = bands;
		this.directory = directory;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_BAND)
	@Transactional(readOnly = true)
	public Band current() {
		return currentBand();
	}

	/** Name and slug only, for the login page of the band host (no authentication). */
	@Transactional(readOnly = true)
	public Band publicProfile() {
		return currentBand();
	}

	/** Null values are left unchanged. The slug is immutable in v1. */
	@PreAuthorize(Permissions.CAN_MANAGE_BAND)
	@Transactional
	public Band update(String name, String timezone) {
		Band band = currentBand();
		if (timezone != null) {
			try {
				band.changeTimezone(ZoneId.of(timezone).getId(), clock.instant());
			}
			catch (DateTimeException ex) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.TIMEZONE_INVALID,
						"Use an IANA time zone such as America/Sao_Paulo.", "timezone");
			}
		}
		if (name != null) {
			band.rename(name.strip(), clock.instant());
		}
		bands.flush();
		directory.evict(band.getSlug());
		return band;
	}

	private Band currentBand() {
		UUID bandId = TenantContextHolder.require().bandId();
		return bands.findById(bandId).orElseThrow(ApiException::notFound);
	}

}
