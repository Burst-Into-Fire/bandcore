package com.burstintofire.bandcore.merch.event;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Events (shows) of the current band. */
@RestController
@RequestMapping("/events")
@Tag(name = "Events")
class EventController {

	private final Events events;

	EventController(Events events) {
		this.events = events;
	}

	@GetMapping
	@Operation(operationId = "listEvents", summary = "List upcoming, past, or all events of the current band",
			description = "UPCOMING is the default. Upcoming events are ordered by start time; past events are newest first.")
	List<EventResponse> list(@RequestParam(defaultValue = "UPCOMING") Events.Period period) {
		return events.list(period).stream().map(EventResponse::of).toList();
	}

	@GetMapping("/{eventId}")
	@Operation(operationId = "getEvent", summary = "Get one event of the current band")
	EventResponse get(@PathVariable UUID eventId) {
		return EventResponse.of(events.get(eventId));
	}

	@PostMapping
	@Operation(operationId = "createEvent", summary = "Create an open event (admins and members)")
	ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
		Event created = events.create(new Events.NewEvent(request.name(), request.venue(), request.city(),
				request.startsAt()));
		return ResponseEntity.created(URI.create("/api/events/" + created.getId())).body(EventResponse.of(created));
	}

	@PatchMapping("/{eventId}")
	@Operation(operationId = "updateEvent", summary = "Edit an event (admins and members)",
			description = "Send the version that was read: 409 VERSION_CONFLICT when it changed meanwhile. "
					+ "Empty venue or city clears that field; omitted fields stay unchanged.")
	EventResponse update(@PathVariable UUID eventId, @Valid @RequestBody UpdateEventRequest request) {
		return EventResponse.of(events.update(eventId, new Events.EventChanges(request.version(), request.name(),
				request.venue(), request.city(), request.startsAt())));
	}

	record CreateEventRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = Event.MAX_NAME_LENGTH) String name,
			@Size(max = Event.MAX_VENUE_LENGTH) String venue,
			@Size(max = Event.MAX_CITY_LENGTH) String city,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ISO-8601 date and time with offset")
			@NotNull Instant startsAt) {
	}

	record UpdateEventRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The version that was read")
			@NotNull @jakarta.validation.constraints.PositiveOrZero Long version,
			@Size(min = 1, max = Event.MAX_NAME_LENGTH) @Pattern(regexp = ".*\\S.*") String name,
			@Size(max = Event.MAX_VENUE_LENGTH) String venue,
			@Size(max = Event.MAX_CITY_LENGTH) String city,
			Instant startsAt) {
	}

	record EventResponse(UUID id, String name, String venue, String city, Instant startsAt, EventStatus status,
			Instant closedAt, UUID closedBy, long version, Instant createdAt, Instant updatedAt) {

		static EventResponse of(Event event) {
			return new EventResponse(event.getId(), event.getName(), event.getVenue(), event.getCity(),
					event.getStartsAt(), event.getStatus(), event.getClosedAt(), event.getClosedBy(),
					event.getVersion(), event.getCreatedAt(), event.getUpdatedAt());
		}

	}

}
