package com.burstintofire.bandcore.finance.category;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Expense categories of the current band. Everyone reads them; admins create, rename, recolor,
 * archive and delete them. Names are unique per band regardless of case, archived ones included.
 */
@Service
public class ExpenseCategories {

	private final ExpenseCategoryRepository categories;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	ExpenseCategories(ExpenseCategoryRepository categories, AuditRecorder audit, IdGenerator ids, Clock clock) {
		this.categories = categories;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public List<ExpenseCategory> list() {
		return categories.findByBandId(bandId());
	}

	/** Another band's category answers 404, exactly like a category that does not exist. */
	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public ExpenseCategory get(UUID categoryId) {
		return find(categoryId);
	}

	@PreAuthorize(Permissions.CAN_MANAGE_FINANCE)
	@Transactional
	public ExpenseCategory create(String name, String color) {
		UUID id = ids.newId();
		String cleanName = name.strip();
		ensureNameFree(cleanName, id);
		ExpenseCategory category = new ExpenseCategory(id, bandId(), cleanName, normalizeColor(color), clock.instant());
		saveAndFlush(category);
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("name", category.getName());
		changes.put("color", category.getColor());
		audit.record("expense_category.created", "expense_category", category.getId(), changes);
		return category;
	}

	/** Null leaves a field unchanged; an empty color clears it. Renaming a default drops its key. */
	@PreAuthorize(Permissions.CAN_MANAGE_FINANCE)
	@Transactional
	public ExpenseCategory update(UUID categoryId, String name, String color) {
		ExpenseCategory category = find(categoryId);
		Instant now = clock.instant();
		Map<String, Object> changes = new LinkedHashMap<>();
		if (name != null && !name.strip().equals(category.getName())) {
			String cleanName = name.strip();
			ensureNameFree(cleanName, category.getId());
			changes.put("name", AuditRecorder.change(category.getName(), cleanName));
			category.rename(cleanName, now);
		}
		if (color != null && !Objects.equals(normalizeColor(color), category.getColor())) {
			changes.put("color", AuditRecorder.change(category.getColor(), normalizeColor(color)));
			category.changeColor(normalizeColor(color), now);
		}
		if (!changes.isEmpty()) {
			saveAndFlush(category);
			audit.record("expense_category.updated", "expense_category", category.getId(), changes);
		}
		return category;
	}

	/** Archived categories stay valid on existing expenses but cannot be chosen for new ones. */
	@PreAuthorize(Permissions.CAN_MANAGE_FINANCE)
	@Transactional
	public ExpenseCategory archive(UUID categoryId) {
		ExpenseCategory category = find(categoryId);
		if (!category.isArchived()) {
			category.archive(clock.instant());
			audit.record("expense_category.archived", "expense_category", category.getId(), Map.of());
		}
		return category;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_FINANCE)
	@Transactional
	public ExpenseCategory unarchive(UUID categoryId) {
		ExpenseCategory category = find(categoryId);
		if (category.isArchived()) {
			category.unarchive(clock.instant());
			audit.record("expense_category.unarchived", "expense_category", category.getId(), Map.of());
		}
		return category;
	}

	/** Only a category no expense uses can be deleted; otherwise 409 CATEGORY_IN_USE. */
	@PreAuthorize(Permissions.CAN_MANAGE_FINANCE)
	@Transactional
	public void delete(UUID categoryId) {
		ExpenseCategory category = find(categoryId);
		try {
			categories.delete(category);
			categories.flush();
		}
		catch (DataIntegrityViolationException referenced) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CATEGORY_IN_USE,
					"Expenses use this category. Archive it instead.");
		}
		audit.record("expense_category.deleted", "expense_category", categoryId, Map.of("name", category.getName()));
	}

	/**
	 * Creates the default categories of a new band, inside the band creation transaction (whose
	 * row-level security context is already the new band's).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	void createDefaults(UUID bandId) {
		Instant now = clock.instant();
		for (DefaultCategory key : DefaultCategory.values()) {
			categories.save(ExpenseCategory.ofDefault(ids.newId(), bandId, key, now));
		}
		categories.flush();
	}

	private ExpenseCategory find(UUID categoryId) {
		return categories.findByBandIdAndId(bandId(), categoryId).orElseThrow(ApiException::notFound);
	}

	private void ensureNameFree(String name, UUID exceptId) {
		if (categories.nameTaken(bandId(), name, exceptId)) {
			throw nameTaken();
		}
	}

	/** The unique index is the last word when two admins pick the same name at once. */
	private void saveAndFlush(ExpenseCategory category) {
		try {
			categories.saveAndFlush(category);
		}
		catch (DataIntegrityViolationException duplicate) {
			throw nameTaken();
		}
	}

	private static ApiException nameTaken() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.CATEGORY_NAME_TAKEN,
				"Another category already has this name.", "name");
	}

	private static String normalizeColor(String color) {
		return color == null || color.isBlank() ? null : color.strip().toLowerCase(Locale.ROOT);
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}
