package com.burstintofire.bandcore.finance.expense;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.finance.category.ExpenseCategory;
import com.burstintofire.bandcore.finance.category.ExpenseCategoryRepository;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.money.Money;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.BandMembers;

/**
 * Expenses of the current band. Everyone reads them; members with write access create them and
 * change their own (created by them, or paid by the member linked to their account); admins
 * change any. Every change is audited with a diff, and edits carry the version that was read.
 */
@Service
public class Expenses {

	/** What a create request says; nulls are refused by the controller except where noted. */
	public record NewExpense(String description, UUID categoryId, Currency currency, long amountCents,
			LocalDate expenseDate, ExpenseStatus status, UUID paidByMemberId, LocalDate paidOn, String notes) {
	}

	/** A partial update: null leaves a field unchanged; empty notes clear them. */
	public record ExpenseChanges(long version, String description, UUID categoryId, Currency currency,
			Long amountCents, LocalDate expenseDate, UUID paidByMemberId, LocalDate paidOn, String notes) {
	}

	/** What the current user may do with an expense. */
	public record Access(boolean edit, boolean delete) {
	}

	public record ExpenseDetails(Expense expense, Access access) {
	}

	private final ExpenseRepository expenses;

	private final ExpenseSearch search;

	private final ExpenseCategoryRepository categories;

	private final BandMembers members;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	Expenses(ExpenseRepository expenses, ExpenseSearch search, ExpenseCategoryRepository categories,
			BandMembers members, AuditRecorder audit, IdGenerator ids, Clock clock) {
		this.expenses = expenses;
		this.search = search;
		this.categories = categories;
		this.members = members;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public Page<ExpenseDetails> list(ExpenseSearch.Filter filter, ExpenseSearch.Sort sort, int page, int size) {
		Ownership ownership = ownership();
		return search.search(bandId(), filter, sort, page, size).map(expense -> details(expense, ownership));
	}

	/** A deleted expense, or another band's, answers 404. */
	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public ExpenseDetails get(UUID expenseId) {
		return details(find(expenseId), ownership());
	}

	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public ExpenseDetails create(NewExpense input) {
		Instant now = clock.instant();
		Expense expense = new Expense(ids.newId(), bandId(), CurrentUser.require().userId(), now);
		expense.describe(input.description().strip(), usableCategory(input.categoryId()), blankToNull(input.notes()));
		expense.changeAmount(Money.of(input.amountCents(), input.currency()), input.expenseDate());
		if (input.status() == ExpenseStatus.PAID) {
			if (input.paidByMemberId() == null) {
				throw payerRequired();
			}
			expense.markPaid(activeMember(input.paidByMemberId(), "paidByMemberId"),
					input.paidOn() != null ? input.paidOn() : input.expenseDate());
		}
		else {
			expense.markPlanned();
		}
		expenses.saveAndFlush(expense);
		audit.record("expense.created", "expense", expense.getId(), expense.snapshot());
		return details(expense, ownership());
	}

	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public ExpenseDetails update(UUID expenseId, ExpenseChanges changes) {
		Expense expense = find(expenseId);
		Ownership ownership = ownership();
		requireOwnership(expense, ownership);
		requireVersion(expense, changes.version());
		Map<String, Object> before = expense.snapshot();

		UUID categoryId = expense.getCategoryId();
		if (changes.categoryId() != null && !changes.categoryId().equals(categoryId)) {
			categoryId = usableCategory(changes.categoryId());
		}
		expense.describe(changes.description() != null ? changes.description().strip() : expense.getDescription(),
				categoryId, changes.notes() != null ? blankToNull(changes.notes()) : expense.getNotes());
		expense.changeAmount(
				Money.of(changes.amountCents() != null ? changes.amountCents() : expense.getAmountCents(),
						changes.currency() != null ? changes.currency() : expense.getCurrency()),
				changes.expenseDate() != null ? changes.expenseDate() : expense.getExpenseDate());
		if (changes.paidByMemberId() != null || changes.paidOn() != null) {
			if (!expense.isPaid()) {
				throw new ApiException(HttpStatus.CONFLICT, ErrorCode.EXPENSE_NOT_PAID,
						"This expense is planned. Mark it as paid to say who paid it.");
			}
			UUID payer = expense.getPaidByMemberId();
			if (changes.paidByMemberId() != null && !changes.paidByMemberId().equals(payer)) {
				payer = activeMember(changes.paidByMemberId(), "paidByMemberId");
			}
			expense.markPaid(payer, changes.paidOn() != null ? changes.paidOn() : expense.getPaidOn());
		}

		Map<String, Object> diff = AuditRecorder.diff(before, expense.snapshot());
		if (!diff.isEmpty()) {
			expense.touch(clock.instant());
			flush(expense);
			audit.record("expense.updated", "expense", expense.getId(), diff);
		}
		return details(expense, ownership);
	}

	/**
	 * Paid expenses are soft-deleted: they leave balances and lists but stay for the audit trail.
	 * Planned expenses never moved money and are deleted.
	 */
	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public void delete(UUID expenseId, Long version) {
		Expense expense = find(expenseId);
		requireOwnership(expense, ownership());
		if (version != null) {
			requireVersion(expense, version);
		}
		Map<String, Object> changes = new LinkedHashMap<>(expense.snapshot());
		if (expense.isPaid()) {
			expense.softDelete(clock.instant());
			flush(expense);
			changes.put("soft_deleted", true);
		}
		else {
			expenses.delete(expense);
			expenses.flush();
			changes.put("soft_deleted", false);
		}
		audit.record("expense.deleted", "expense", expenseId, changes);
	}

	// ---- helpers shared with the other expense use cases ----

	Expense find(UUID expenseId) {
		return expenses.findByBandIdAndIdAndDeletedAtIsNull(bandId(), expenseId).orElseThrow(ApiException::notFound);
	}

	Ownership ownership() {
		UUID userId = CurrentUser.require().userId();
		return new Ownership(userId, members.memberOf(userId), CurrentUser.hasPermission(Permissions.FINANCE_MANAGE),
				CurrentUser.hasPermission(Permissions.FINANCE_WRITE));
	}

	ExpenseDetails details(Expense expense, Ownership ownership) {
		boolean owns = ownership.mayChange(expense);
		return new ExpenseDetails(expense, new Access(owns, owns));
	}

	/** Visible but not theirs: 403, as docs/security/permissions.md says. */
	static void requireOwnership(Expense expense, Ownership ownership) {
		if (!ownership.mayChange(expense)) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
					"Only admins change expenses created or paid by someone else.");
		}
	}

	static void requireVersion(Expense expense, long version) {
		if (expense.getVersion() != version) {
			throw versionConflict();
		}
	}

	/** Two requests with the same version: the second flush fails on the version column. */
	void flush(Expense expense) {
		try {
			expenses.saveAndFlush(expense);
		}
		catch (ObjectOptimisticLockingFailureException concurrent) {
			throw versionConflict();
		}
	}

	/** Unknown ids, and other bands' ids, answer 404, like any other band's resource. */
	UUID activeMember(UUID memberId, String field) {
		Boolean active = members.activeStatus(List.of(memberId)).get(memberId);
		if (active == null) {
			throw ApiException.notFound();
		}
		if (!active) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.MEMBER_INACTIVE,
					"This member has left the band.", field);
		}
		return memberId;
	}

	static ApiException payerRequired() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PAYER_REQUIRED,
				"Say which member paid this expense.", "paidByMemberId");
	}

	private UUID usableCategory(UUID categoryId) {
		ExpenseCategory category = categories.findByBandIdAndId(bandId(), categoryId)
			.orElseThrow(ApiException::notFound);
		if (category.isArchived()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.CATEGORY_ARCHIVED,
					"This category is archived. Choose another one.", "categoryId");
		}
		return category.getId();
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
				"Someone else changed this expense. Reload it and try again.");
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

	/** Who the current user is, for "own expenses" checks; computed once per request. */
	record Ownership(UUID userId, Optional<UUID> memberId, boolean manage, boolean write) {

		boolean mayChange(Expense expense) {
			if (manage) {
				return true;
			}
			if (!write) {
				return false;
			}
			return userId.equals(expense.getCreatedBy())
					|| (expense.getPaidByMemberId() != null && memberId.filter(expense.getPaidByMemberId()::equals)
						.isPresent());
		}

	}

}
