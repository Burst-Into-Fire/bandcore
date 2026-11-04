package com.burstintofire.bandcore.merch.stock;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * The only way stock changes (RN03). Each change updates the variant's level and inserts its
 * movement in one statement, and only when the result is not negative: under any concurrency,
 * stock never goes below zero and the movements always add up to the quantity on hand. Callers
 * check permissions; this class is internal to the merch module.
 */
@Service
public class StockService {

	/** A change to one variant's quantity on hand. */
	public record Change(UUID variantId, int delta) {
	}

	/** What caused a movement, for example a production batch. */
	public record Source(String type, UUID id) {

		public static Source batch(UUID batchId) {
			return new Source("BATCH", batchId);
		}

	}

	/** A movement that was recorded, with the quantity it left on hand. */
	public record Applied(UUID movementId, UUID variantId, int delta, int quantityAfter) {
	}

	/** A variant's stock right now. */
	public record Level(UUID variantId, int quantityOnHand, UUID lastMovementId) {
	}

	public record Movement(UUID id, MovementType type, int quantityDelta, int quantityAfter, String sourceType,
			UUID sourceId, String reason, String note, UUID actorUserId, Instant occurredAt) {
	}

	/** A variant whose quantity on hand does not match its movements; never expected. */
	public record Drift(UUID variantId, int quantityOnHand, long movementsTotal) {
	}

	/** Far above any real batch or count, and far below an integer overflow. */
	public static final int MAX_DELTA = 1_000_000;

	private static final String APPLY = """
			WITH changed AS (
			    UPDATE stock_levels
			    SET quantity_on_hand = quantity_on_hand + ?::integer, last_movement_id = ?::uuid, updated_at = ?::timestamptz
			    WHERE band_id = ?::uuid AND variant_id = ?::uuid AND quantity_on_hand + ?::integer >= 0
			    RETURNING band_id, variant_id, quantity_on_hand)
			INSERT INTO stock_movements (id, band_id, variant_id, type, quantity_delta, quantity_after, source_type,
			    source_id, reason, note, actor_user_id, occurred_at)
			SELECT ?::uuid, band_id, variant_id, ?::text, ?::integer, quantity_on_hand, ?::text, ?::uuid, ?::text,
			    ?::text, ?::uuid, ?::timestamptz
			FROM changed
			RETURNING quantity_after
			""";

	private static final String LEVELS = """
			SELECT variant_id, quantity_on_hand, last_movement_id FROM stock_levels
			WHERE band_id = ? AND variant_id = ANY (?)
			ORDER BY variant_id
			""";

	private static final String MOVEMENTS = """
			SELECT id, type, quantity_delta, quantity_after, source_type, source_id, reason, note, actor_user_id,
			    occurred_at
			FROM stock_movements
			WHERE band_id = ? AND variant_id = ?
			ORDER BY sequence DESC
			LIMIT ? OFFSET ?
			""";

	private static final String DRIFT = """
			SELECT l.variant_id, l.quantity_on_hand, coalesce(sum(m.quantity_delta), 0)
			FROM stock_levels l
			LEFT JOIN stock_movements m ON m.band_id = l.band_id AND m.variant_id = l.variant_id
			WHERE l.band_id = ?
			GROUP BY l.variant_id, l.quantity_on_hand
			HAVING l.quantity_on_hand <> coalesce(sum(m.quantity_delta), 0)
			""";

	private final JdbcTemplate jdbc;

	private final IdGenerator ids;

	private final Clock clock;

	StockService(JdbcTemplate jdbc, IdGenerator ids, Clock clock) {
		this.jdbc = jdbc;
		this.ids = ids;
		this.clock = clock;
	}

	/** Starts the stock of a new variant at zero, in the transaction that creates it. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void open(UUID variantId) {
		jdbc.update("INSERT INTO stock_levels (band_id, variant_id, updated_at) VALUES (?, ?, ?)", bandId(), variantId,
				Timestamp.from(clock.instant()));
	}

	/**
	 * Applies the changes in order of variant id, so two operations on the same variants never
	 * wait for each other's locks in opposite orders. All or nothing: a change that would go below
	 * zero throws 409 INSUFFICIENT_STOCK (with the variant and what is available) and the caller's
	 * transaction rolls back; a variant of another band answers 404.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Applied> apply(MovementType type, Collection<Change> changes, Source source) {
		List<Applied> applied = new ArrayList<>();
		for (Change change : changes.stream().sorted(Comparator.comparing(c -> c.variantId().toString())).toList()) {
			applied.add(record(type, change, source, null, null));
		}
		return applied;
	}

	/**
	 * Locks the stock of the variants until the transaction ends, in order of variant id, and
	 * returns it. For changes computed from the current quantity (a count, a batch's average).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Map<UUID, Level> lock(Collection<UUID> variantIds) {
		return query(LEVELS + " FOR UPDATE", variantIds);
	}

	/** The current stock of the variants of the current band; others are left out. */
	@Transactional(readOnly = true)
	public Map<UUID, Level> levels(Collection<UUID> variantIds) {
		return query(LEVELS, variantIds);
	}

	/** Whether any of the variants ever moved stock. */
	@Transactional(readOnly = true)
	public boolean anyMovements(Collection<UUID> variantIds) {
		if (variantIds.isEmpty()) {
			return false;
		}
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM stock_movements WHERE band_id = ? AND variant_id = ANY (?))", Boolean.class,
				bandId(), uuids(variantIds)));
	}

	/** The movements of a variant, newest first. Another band's variant answers 404. */
	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public Page<Movement> movements(UUID variantId, int page, int size) {
		if (levels(List.of(variantId)).isEmpty()) {
			throw ApiException.notFound();
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE band_id = ? AND variant_id = ?",
				Long.class, bandId(), variantId);
		List<Movement> movements = jdbc.query(MOVEMENTS,
				(rs, row) -> new Movement(rs.getObject(1, UUID.class), MovementType.valueOf(rs.getString(2)), rs.getInt(3),
						rs.getInt(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8),
						rs.getObject(9, UUID.class), rs.getTimestamp(10).toInstant()),
				bandId(), variantId, size, (long) page * size);
		return new PageImpl<>(movements, PageRequest.of(page, size), total == null ? 0 : total);
	}

	/**
	 * Variants of the current band whose quantity on hand differs from the sum of their
	 * movements. Always empty unless something bypassed this service.
	 */
	@Transactional(readOnly = true)
	public List<Drift> drift() {
		return jdbc.query(DRIFT, (rs, row) -> new Drift(rs.getObject(1, UUID.class), rs.getInt(2), rs.getLong(3)),
				bandId());
	}

	private Applied record(MovementType type, Change change, Source source, String reason, String note) {
		if (change.delta() == 0 || Math.abs(change.delta()) > MAX_DELTA) {
			throw new IllegalArgumentException("A stock change moves between 1 and " + MAX_DELTA + " units");
		}
		UUID movementId = ids.newId();
		Timestamp now = Timestamp.from(clock.instant());
		List<Integer> after = jdbc.queryForList(APPLY, Integer.class, change.delta(), movementId, now, bandId(),
				change.variantId(), change.delta(), movementId, type.name(), change.delta(),
				source == null ? null : source.type(), source == null ? null : source.id(), reason, note,
				CurrentUser.id().orElse(null), now);
		if (after.isEmpty()) {
			Level level = levels(List.of(change.variantId())).get(change.variantId());
			if (level == null) {
				throw ApiException.notFound();
			}
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK,
					"Not enough stock for this change.")
				.withProperty("variantId", change.variantId())
				.withProperty("available", level.quantityOnHand());
		}
		return new Applied(movementId, change.variantId(), change.delta(), after.getFirst());
	}

	private Map<UUID, Level> query(String sql, Collection<UUID> variantIds) {
		Map<UUID, Level> levels = new LinkedHashMap<>();
		if (variantIds.isEmpty()) {
			return levels;
		}
		jdbc.query(sql, rs -> {
			Level level = new Level(rs.getObject(1, UUID.class), rs.getInt(2), rs.getObject(3, UUID.class));
			levels.put(level.variantId(), level);
		}, bandId(), uuids(variantIds));
		return levels;
	}

	private static SqlArrayValue uuids(Collection<UUID> ids) {
		return new SqlArrayValue("uuid", ids.stream().distinct().toArray());
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}
