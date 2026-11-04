package com.burstintofire.bandcore.finance.expense;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.burstintofire.bandcore.tenancy.BandCalendar;
import com.burstintofire.bandcore.tenancy.BandMembers;

/**
 * Expenses of the current band and how they are split. Everyone reads them; members with write
 * access create them and change their own (created by them, or paid by the member linked to their
 * account); admins change any. Every change is audited with a diff, and edits carry the version
 * that was read. The shares of an expense always add up to its amount.
 */
@Service
public class Expenses {

	/**
	 * How to split an expense. For {@link SplitMode#EQUAL}, no shares means every active member;
	 * the other modes need a value per member.
	 */
	public record SplitRequest(SplitMode mode, List<SplitCalculator.ShareInput> shares) {
	}

	/** What a create request says; nulls are refused by the controller except where noted. */
	public record NewExpense(String description, UUID categoryId, Currency currency, long amountCents,
			LocalDate expenseDate, ExpenseStatus status, UUID paidByMemberId, LocalDate paidOn, String notes,
			SplitRequest split) {
	}

	/**
	 * A partial update: null leaves a field unchanged; empty notes clear them. Without a new split,
	 * equal and percentage splits follow a new amount; custom amounts must be sent again.
	 */
	public record ExpenseChanges(long version, String description, UUID categoryId, Currency currency,
			Long amountCents, LocalDate expenseDate, UUID paidByMemberId, LocalDate paidOn, String notes,
			SplitRequest split) {
	}

	/** What the current user may do with an expense. */
	public record Access(boolean edit, boolean delete, boolean pay, boolean revertPayment) {
	}

	/** @param overdue planned, and its date is before today in the band's time zone */
	public record ExpenseDetails(Expense expense, List<ExpenseSplit> shares, Access access, boolean overdue) {
	}

	/** Upcoming lists look at most this far ahead. */
	public static final int MAX_UPCOMING_DAYS = 366;

	private final ExpenseRepository expenses;

	private final ExpenseSplitRepository splits;

	private final ExpenseSearch search;

	private final ExpenseCategoryRepository categories;

	private final BandMembers members;

	private final BandCalendar calendar;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	Expenses(ExpenseRepository expenses, ExpenseSplitRepository splits, ExpenseSearch search,
			ExpenseCategoryRepository categories, BandMembers members, BandCalendar calendar, AuditRecorder audit,
			IdGenerator ids, Clock clock) {
		this.expenses = expenses;
		this.splits = splits;
		this.search = search;
		this.categories = categories;
		this.members = members;
		this.calendar = calendar;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public Page<ExpenseDetails> list(ExpenseSearch.Filter filter, ExpenseSearch.Sort sort, int page, int size) {
		Ownership ownership = ownership();
		Page<Expense> found = search.search(bandId(), filter, sort, page, size);
		Map<UUID, List<ExpenseSplit>> shares = sharesOf(found.getContent().stream().map(Expense::getId).toList());
		return found.map(expense -> details(expense, shares.getOrDefault(expense.getId(), List.of()), ownership));
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
		return create(input, null, null);
	}

	/**
	 * Creates the expense that pays for another module's record, for example a production batch
	 * (M7-04), in that record's transaction. One expense per source, ever: the unique index
	 * refuses a second one even when the first was deleted.
	 */
	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public ExpenseDetails createFromSource(ExpenseSourceType sourceType, UUID sourceId, NewExpense input) {
		return create(input, sourceType, sourceId);
	}

	private ExpenseDetails create(NewExpense input, ExpenseSourceType sourceType, UUID sourceId) {
		Instant now = clock.instant();
		Expense expense = new Expense(ids.newId(), bandId(), CurrentUser.require().userId(), now);
		if (sourceType != null) {
			expense.linkTo(sourceType, sourceId);
		}
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
		SplitRequest split = input.split() != null ? input.split() : new SplitRequest(SplitMode.EQUAL, null);
		List<SplitCalculator.Share> shares = SplitCalculator.compute(split.mode(), expense.getAmountCents(),
				participants(split, Set.of()));
		expense.changeSplitMode(split.mode());
		expenses.saveAndFlush(expense);
		List<ExpenseSplit> saved = replaceShares(expense, shares);
		Map<String, Object> created = snapshot(expense, saved);
		if (sourceType != null) {
			created.put("source_type", sourceType.name());
			created.put("source_id", sourceId.toString());
		}
		audit.record("expense.created", "expense", expense.getId(), created);
		return details(expense, saved, ownership());
	}

	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public ExpenseDetails update(UUID expenseId, ExpenseChanges changes) {
		Expense expense = find(expenseId);
		Ownership ownership = ownership();
		requireOwnership(expense, ownership);
		requireVersion(expense, changes.version());
		List<ExpenseSplit> current = sharesOf(List.of(expense.getId())).getOrDefault(expense.getId(), List.of());
		Map<String, Object> before = snapshot(expense, current);

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

		List<ExpenseSplit> after = current;
		List<SplitCalculator.Share> currentShares = current.stream().map(ExpenseSplit::toShare).toList();
		List<SplitCalculator.Share> newShares = null;
		if (changes.split() != null) {
			Set<UUID> sharing = current.stream().map(ExpenseSplit::getMemberId).collect(Collectors.toSet());
			newShares = SplitCalculator.compute(changes.split().mode(), expense.getAmountCents(),
					participants(changes.split(), sharing));
			expense.changeSplitMode(changes.split().mode());
		}
		else if (sumOf(currentShares) != expense.getAmountCents()) {
			// The amount changed: equal and percentage splits follow it, custom amounts must be resent.
			newShares = SplitCalculator.compute(expense.getSplitMode(), expense.getAmountCents(),
					SplitCalculator.inputsOf(currentShares));
		}
		if (newShares != null && !newShares.equals(currentShares)) {
			after = replaceShares(expense, newShares);
		}

		Map<String, Object> diff = AuditRecorder.diff(before, snapshot(expense, after));
		if (!diff.isEmpty()) {
			expense.touch(clock.instant());
			flush(expense);
			audit.record("expense.updated", "expense", expense.getId(), diff);
		}
		return details(expense, after, ownership);
	}

	/**
	 * Planned expenses still to pay, overdue ones first, up to {@code days} from today in the
	 * band's time zone; for the dashboard.
	 */
	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public List<ExpenseDetails> upcoming(int days) {
		Ownership ownership = ownership();
		List<Expense> planned = expenses.findUpcoming(bandId(), calendar.today().plusDays(days));
		Map<UUID, List<ExpenseSplit>> shares = sharesOf(planned.stream().map(Expense::getId).toList());
		return planned.stream()
			.map(expense -> details(expense, shares.getOrDefault(expense.getId(), List.of()), ownership))
			.toList();
	}

	/**
	 * PLANNED to PAID: the move that changes balances. Who may do it: admins; the member who
	 * created it; and any member paying it themselves. Without a payer, the caller's own member
	 * pays; without a date, it is paid today.
	 */
	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public ExpenseDetails pay(UUID expenseId, UUID payerMemberId, LocalDate paidOn, Long version) {
		Expense expense = find(expenseId);
		Ownership ownership = ownership();
		UUID payer = payerMemberId != null ? payerMemberId : ownership.memberId().orElseThrow(Expenses::payerRequired);
		if (!ownership.mayPay(expense, payer)) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
					"Only admins and its creator mark someone else's payment.");
		}
		if (version != null) {
			requireVersion(expense, version);
		}
		if (expense.isPaid()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.EXPENSE_NOT_PLANNED, "This expense is already paid.");
		}
		expense.markPaid(activeMember(payer, "payerMemberId"), paidOn != null ? paidOn : calendar.today());
		expense.touch(clock.instant());
		flush(expense);
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("paid_by_member_id", expense.getPaidByMemberId().toString());
		changes.put("paid_on", expense.getPaidOn().toString());
		changes.put("amount_cents", expense.getAmountCents());
		changes.put("currency", expense.getCurrency().name());
		audit.record("expense.paid", "expense", expense.getId(), changes);
		return details(expense, ownership);
	}

	/** PAID back to PLANNED, for payments recorded by mistake (admins). */
	@PreAuthorize(Permissions.CAN_MANAGE_FINANCE)
	@Transactional
	public ExpenseDetails revertPayment(UUID expenseId, Long version) {
		Expense expense = find(expenseId);
		if (version != null) {
			requireVersion(expense, version);
		}
		if (!expense.isPaid()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.EXPENSE_NOT_PAID, "This expense is not paid.");
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("paid_by_member_id", AuditRecorder.change(expense.getPaidByMemberId().toString(), null));
		changes.put("paid_on", AuditRecorder.change(expense.getPaidOn().toString(), null));
		expense.markPlanned();
		expense.touch(clock.instant());
		flush(expense);
		audit.record("expense.payment_reverted", "expense", expense.getId(), changes);
		return details(expense, ownership());
	}

	/**
	 * Paid expenses are soft-deleted: they leave balances and lists but stay for the audit trail.
	 * Planned expenses never moved money and are deleted with their shares.
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
			splits.deleteByExpense(bandId(), expense.getId());
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
		return details(expense, sharesOf(List.of(expense.getId())).getOrDefault(expense.getId(), List.of()), ownership);
	}

	ExpenseDetails details(Expense expense, List<ExpenseSplit> shares, Ownership ownership) {
		boolean overdue = !expense.isPaid() && expense.getExpenseDate().isBefore(calendar.today());
		return new ExpenseDetails(expense, shares, access(expense, ownership), overdue);
	}

	static Access access(Expense expense, Ownership ownership) {
		boolean owns = ownership.mayChange(expense);
		boolean pay = !expense.isPaid()
				&& (owns || ownership.memberId().filter(member -> ownership.mayPay(expense, member)).isPresent());
		return new Access(owns, owns, pay, expense.isPaid() && ownership.manage());
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

	Map<String, Object> snapshot(Expense expense, List<ExpenseSplit> shares) {
		Map<String, Object> snapshot = expense.snapshot();
		snapshot.put("split_mode", expense.getSplitMode() == null ? null : expense.getSplitMode().name());
		Map<String, Long> byMember = new TreeMap<>();
		shares.forEach(share -> byMember.put(share.getMemberId().toString(), share.getShareCents()));
		snapshot.put("shares", byMember);
		return snapshot;
	}

	/**
	 * The members of a split. Members already sharing the expense may stay even if they have left
	 * the band since; new ones must be active members of this band (404 for unknown ids).
	 */
	private List<SplitCalculator.ShareInput> participants(SplitRequest split, Set<UUID> alreadySharing) {
		if (split.mode() == SplitMode.EQUAL && (split.shares() == null || split.shares().isEmpty())) {
			return members.activeMemberIds()
				.stream()
				.map(memberId -> new SplitCalculator.ShareInput(memberId, null, null))
				.toList();
		}
		List<SplitCalculator.ShareInput> inputs = split.shares() == null ? List.of() : split.shares();
		List<UUID> joining = inputs.stream()
			.map(SplitCalculator.ShareInput::memberId)
			.filter(memberId -> memberId != null && !alreadySharing.contains(memberId))
			.distinct()
			.toList();
		Map<UUID, Boolean> status = members.activeStatus(joining);
		for (UUID memberId : joining) {
			Boolean active = status.get(memberId);
			if (active == null) {
				throw ApiException.notFound();
			}
			if (!active) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.MEMBER_INACTIVE,
						"A member of this split has left the band.", "split");
			}
		}
		return inputs;
	}

	/** Deletes and inserts in two flushes: the unique (expense, member) key would see both at once. */
	private List<ExpenseSplit> replaceShares(Expense expense, List<SplitCalculator.Share> shares) {
		splits.deleteByExpense(bandId(), expense.getId());
		List<ExpenseSplit> saved = new ArrayList<>(shares.size());
		for (SplitCalculator.Share share : shares) {
			saved.add(splits.save(new ExpenseSplit(ids.newId(), bandId(), expense.getId(), share)));
		}
		splits.flush();
		return List.copyOf(saved);
	}

	private Map<UUID, List<ExpenseSplit>> sharesOf(Collection<UUID> expenseIds) {
		if (expenseIds.isEmpty()) {
			return Map.of();
		}
		return splits.findByBandIdAndExpenseIdIn(bandId(), expenseIds)
			.stream()
			.sorted(Comparator.comparing(ExpenseSplit::getMemberId, SplitCalculator.MEMBER_ORDER))
			.collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));
	}

	private static long sumOf(List<SplitCalculator.Share> shares) {
		return shares.stream().mapToLong(SplitCalculator.Share::shareCents).sum();
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

		/** Admins and the creator record anyone's payment; anyone with write access, their own. */
		boolean mayPay(Expense expense, UUID payer) {
			return mayChange(expense) || (write && memberId.filter(payer::equals).isPresent());
		}

	}

}
