package com.burstintofire.bandcore.tenancy.band;

import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * The current band's own data. The band is always the one of the request host; there is no
 * way to address another band here. Changes are recorded in the audit log. The currency is
 * fixed to BRL and the slug cannot change in v1 (it is the band's address).
 */
@Service
public class BandSettings {

	private final BandRepository bands;

	private final BandDirectory directory;

	private final AuditRecorder audit;

	private final Clock clock;

	BandSettings(BandRepository bands, BandDirectory directory, AuditRecorder audit, Clock clock) {
		this.bands = bands;
		this.directory = directory;
		this.audit = audit;
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
		Map<String, Object> changes = new LinkedHashMap<>();
		String previousTimezone = band.getTimezone();
		String previousName = band.getName();
		if (timezone != null) {
			// Region names from the IANA database only: fixed offsets such as "+03:00" or "UTC+3"
			// would ignore daylight saving changes when months are computed in the band's time zone.
			if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.TIMEZONE_INVALID,
						"Use an IANA time zone such as America/Sao_Paulo.", "timezone");
			}
			band.changeTimezone(timezone, clock.instant());
		}
		if (name != null) {
			band.rename(name.strip(), clock.instant());
		}
		if (!band.getName().equals(previousName)) {
			changes.put("name", AuditRecorder.change(previousName, band.getName()));
		}
		if (!band.getTimezone().equals(previousTimezone)) {
			changes.put("timezone", AuditRecorder.change(previousTimezone, band.getTimezone()));
		}
		if (!changes.isEmpty()) {
			audit.record("band.updated", "band", band.getId(), changes);
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
