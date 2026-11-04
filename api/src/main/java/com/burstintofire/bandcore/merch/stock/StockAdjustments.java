package com.burstintofire.bandcore.merch.stock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Manual stock adjustments (M7-05, RF25): physical counts drift, and items get damaged, lost or
 * given away. Admins correct the stock with a reason that stays on record; it never goes below
 * zero and the average unit cost does not change.
 */
@Service
public class StockAdjustments {

	public static final int MAX_NOTE_LENGTH = 500;

	private final StockService stock;

	private final AuditRecorder audit;

	StockAdjustments(StockService stock, AuditRecorder audit) {
		this.stock = stock;
		this.audit = audit;
	}

	/**
	 * @param countedQuantity the quantity found in a count, or null with {@code delta}
	 * @param delta units found (positive) or gone (negative), or null with {@code countedQuantity}
	 */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public StockService.Movement adjust(UUID variantId, Integer countedQuantity, Integer delta,
			AdjustmentReason reason, String note) {
		if ((countedQuantity == null) == (delta == null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
					"Give either the counted quantity or the change.", "countedQuantity");
		}
		String cleanNote = note == null || note.isBlank() ? null : note.strip();
		if (reason == AdjustmentReason.OTHER && cleanNote == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Say what happened.", "note");
		}
		StockService.Applied applied = stock.adjust(variantId, countedQuantity, delta, reason, cleanNote);
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("reason", reason.name());
		changes.put("quantity_delta", applied.delta());
		changes.put("quantity_after", applied.quantityAfter());
		changes.put("note_length", cleanNote == null ? 0 : cleanNote.length());
		audit.record("stock.adjusted", "product_variant", variantId, changes);
		return stock.movement(applied.movementId());
	}

}
