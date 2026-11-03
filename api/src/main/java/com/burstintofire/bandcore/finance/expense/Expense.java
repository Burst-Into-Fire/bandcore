package com.burstintofire.bandcore.finance.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.money.Money;
import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Tenant table: an expense of the band, planned or paid, in one currency. Paid expenses are
 * soft-deleted (kept for the audit trail, excluded from balances); planned ones are deleted.
 */
@Entity
@Table(name = "expenses")
public class Expense extends AssignedIdEntity {

	public static final int MAX_DESCRIPTION_LENGTH = 120;

	public static final int MAX_NOTES_LENGTH = 500;

	/** R$ 10 billion: far from overflowing when shares are computed in basis points. */
	public static final long MAX_AMOUNT_CENTS = 1_000_000_000_000L;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(nullable = false)
	private String description;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Currency currency;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "expense_date", nullable = false)
	private LocalDate expenseDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExpenseStatus status;

	@Column(name = "paid_by_member_id")
	private UUID paidByMemberId;

	@Column(name = "paid_on")
	private LocalDate paidOn;

	@Column
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(name = "split_mode", nullable = false)
	private SplitMode splitMode;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Expense() {
	}

	Expense(UUID id, UUID bandId, UUID createdBy, Instant now) {
		super(id);
		this.bandId = bandId;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getDescription() {
		return description;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public Currency getCurrency() {
		return currency;
	}

	public long getAmountCents() {
		return amountCents;
	}

	public Money amount() {
		return Money.of(amountCents, currency);
	}

	public LocalDate getExpenseDate() {
		return expenseDate;
	}

	public ExpenseStatus getStatus() {
		return status;
	}

	public boolean isPaid() {
		return status == ExpenseStatus.PAID;
	}

	public UUID getPaidByMemberId() {
		return paidByMemberId;
	}

	public LocalDate getPaidOn() {
		return paidOn;
	}

	public String getNotes() {
		return notes;
	}

	public SplitMode getSplitMode() {
		return splitMode;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	void describe(String newDescription, UUID newCategoryId, String newNotes) {
		this.description = newDescription;
		this.categoryId = newCategoryId;
		this.notes = newNotes;
	}

	void changeAmount(Money newAmount, LocalDate newExpenseDate) {
		this.currency = newAmount.currency();
		this.amountCents = newAmount.cents();
		this.expenseDate = newExpenseDate;
	}

	void changeSplitMode(SplitMode newMode) {
		this.splitMode = newMode;
	}

	void markPlanned() {
		this.status = ExpenseStatus.PLANNED;
		this.paidByMemberId = null;
		this.paidOn = null;
	}

	void markPaid(UUID payerMemberId, LocalDate paidOnDate) {
		this.status = ExpenseStatus.PAID;
		this.paidByMemberId = payerMemberId;
		this.paidOn = paidOnDate;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	void softDelete(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}

	/** The audited fields, for diffs in the audit log. Notes are free text: only whether they changed. */
	Map<String, Object> snapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("description", description);
		snapshot.put("category_id", categoryId == null ? null : categoryId.toString());
		snapshot.put("currency", currency == null ? null : currency.name());
		snapshot.put("amount_cents", amountCents);
		snapshot.put("expense_date", expenseDate == null ? null : expenseDate.toString());
		snapshot.put("status", status == null ? null : status.name());
		snapshot.put("paid_by_member_id", paidByMemberId == null ? null : paidByMemberId.toString());
		snapshot.put("paid_on", paidOn == null ? null : paidOn.toString());
		snapshot.put("notes_length", notes == null ? 0 : notes.length());
		return snapshot;
	}

}
