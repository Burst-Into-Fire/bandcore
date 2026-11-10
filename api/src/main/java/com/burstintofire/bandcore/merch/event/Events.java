package com.burstintofire.bandcore.merch.event;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/** Shows of the current band. Admins and members can create and edit them. */
@Service
public class Events {

	public record NewEvent(String name, String venue, String city, Instant startsAt) {
	}

	public record EventChanges(long version, String name, String venue, String city, Instant startsAt) {
	}

	public enum Period {

		UPCOMING,

		PAST,

		ALL

	}

	private final EventRepository events;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	Events(EventRepository events, AuditRecorder audit, IdGenerator ids, Clock clock) {
		this.events = events;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public List<Event> list(Period period) {
		UUID bandId = bandId();
		Instant now = clock.instant();
		return switch (period) {
			case UPCOMING -> events.findByBandIdAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(bandId, now);
			case PAST -> events.findByBandIdAndStartsAtLessThanOrderByStartsAtDescIdDesc(bandId, now);
			case ALL -> events.findByBandIdOrderByStartsAtAscIdAsc(bandId);
		};
	}

	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public Event get(UUID eventId) {
		return find(eventId);
	}

	@PreAuthorize(Permissions.CAN_WRITE_SALES)
	@Transactional
	public Event create(NewEvent input) {
		Instant now = clock.instant();
		Event event = new Event(ids.newId(), bandId(), CurrentUser.require().userId(), now);
		event.change(input.name().strip(), blankToNull(input.venue()), blankToNull(input.city()), input.startsAt());
		events.save(event);
		audit.record("event.created", "event", event.getId(), event.snapshot());
		return event;
	}

	@PreAuthorize(Permissions.CAN_WRITE_SALES)
	@Transactional
	public Event update(UUID eventId, EventChanges changes) {
		Event event = find(eventId);
		if (event.getVersion() != changes.version()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
					"The event changed since it was read; reload it and try again.", "version");
		}
		Map<String, Object> before = event.snapshot();
		String name = changes.name() != null ? changes.name().strip() : event.getName();
		String venue = changes.venue() != null ? blankToNull(changes.venue()) : event.getVenue();
		String city = changes.city() != null ? blankToNull(changes.city()) : event.getCity();
		Instant startsAt = changes.startsAt() != null ? changes.startsAt() : event.getStartsAt();
		event.change(name, venue, city, startsAt);
		Map<String, Object> diff = AuditRecorder.diff(before, event.snapshot());
		if (!diff.isEmpty()) {
			event.touch(clock.instant());
			events.saveAndFlush(event);
			audit.record("event.updated", "event", event.getId(), diff);
		}
		return event;
	}

	private Event find(UUID eventId) {
		return events.findByBandIdAndId(bandId(), eventId).orElseThrow(ApiException::notFound);
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

}
