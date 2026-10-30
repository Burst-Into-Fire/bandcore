package com.burstintofire.bandcore.tenancy.band;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/** Creates and finds bands. The only writer of the {@code bands} table. */
@Service
public class BandRegistry {

	/** Unique index on {@code lower(slug)}, see V2__create_bands.sql. */
	static final String SLUG_UNIQUE_INDEX = "bands_slug_lower_key";

	public static final int MAX_NAME_LENGTH = 80;

	private final BandRepository bands;

	private final BandDirectory directory;

	private final IdGenerator ids;

	private final Clock clock;

	public BandRegistry(BandRepository bands, BandDirectory directory, IdGenerator ids, Clock clock) {
		this.bands = bands;
		this.directory = directory;
		this.ids = ids;
		this.clock = clock;
	}

	/**
	 * Validates the slug and inserts the band. Joins the caller's transaction, so band creation
	 * can add the first membership atomically. Two concurrent registrations of the same slug
	 * result in exactly one band: the database's unique index decides.
	 */
	@Transactional
	public Band register(String requestedSlug, String name) {
		return register(requestedSlug, name, null);
	}

	/** Same as {@link #register(String, String)}, recording the account that created the band. */
	@Transactional
	public Band register(String requestedSlug, String name, UUID createdBy) {
		String slug = requireAvailable(requestedSlug);
		Band band = new Band(ids.newId(), slug, name.strip(), createdBy, clock.instant());
		try {
			Band saved = bands.saveAndFlush(band);
			directory.evict(slug);
			return saved;
		}
		catch (DataIntegrityViolationException ex) {
			if (isSlugConflict(ex)) {
				throw slugTaken();
			}
			throw ex;
		}
	}

	/** Whether a slug can be used now, and why not. Never throws for bad input. */
	@Transactional(readOnly = true)
	public Availability availability(String requestedSlug) {
		String slug = Slugs.normalize(requestedSlug);
		Optional<Slugs.Problem> problem = Slugs.check(slug);
		if (problem.isPresent()) {
			return new Availability(slug, false, problem.get() == Slugs.Problem.INVALID ? ErrorCode.SLUG_INVALID
					: ErrorCode.SLUG_RESERVED);
		}
		return bands.existsBySlug(slug) ? new Availability(slug, false, ErrorCode.SLUG_TAKEN)
				: new Availability(slug, true, null);
	}

	public record Availability(String slug, boolean available, ErrorCode reason) {
	}

	@Transactional(readOnly = true)
	public long countCreatedBy(UUID userId) {
		return bands.countByCreatedBy(userId);
	}

	@Transactional(readOnly = true)
	public List<Band> findAll(Collection<UUID> ids) {
		return bands.findByIdIn(ids);
	}

	/** Normalized slug, or a 422/409 problem explaining why it cannot be used. */
	@Transactional(readOnly = true)
	public String requireAvailable(String requestedSlug) {
		String slug = Slugs.normalize(requestedSlug);
		Optional<Slugs.Problem> problem = Slugs.check(slug);
		if (problem.isPresent()) {
			throw switch (problem.get()) {
				case INVALID -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SLUG_INVALID,
						"Use 3 to 32 lowercase letters, digits and single hyphens.", "slug");
				case RESERVED -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SLUG_RESERVED,
						"This address is reserved.", "slug");
			};
		}
		if (bands.existsBySlug(slug)) {
			throw slugTaken();
		}
		return slug;
	}

	/** Suspending a band blocks its host on the next request (the slug cache is evicted). */
	@Transactional
	public void changeStatus(UUID bandId, BandStatus status) {
		Band band = bands.findById(bandId).orElseThrow(ApiException::notFound);
		band.changeStatus(status, clock.instant());
		directory.evict(band.getSlug());
	}

	@Transactional(readOnly = true)
	public Optional<Band> findBySlug(String slug) {
		return bands.findBySlug(slug);
	}

	private static ApiException slugTaken() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SLUG_TAKEN, "This address is already in use.", "slug");
	}

	private static boolean isSlugConflict(DataIntegrityViolationException ex) {
		Throwable cause = ex;
		while (cause != null) {
			if (cause.getMessage() != null && cause.getMessage().contains(SLUG_UNIQUE_INDEX)) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

}
