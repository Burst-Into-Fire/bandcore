package com.burstintofire.bandcore.finance.expense;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Tenant table: one member's share of an expense, in cents of the expense's currency. The shares
 * of an expense always add up to its amount (checked by the service and, at commit, by the
 * database). The input of custom splits is kept so they can be edited later.
 */
@Entity
@Table(name = "expense_splits")
public class ExpenseSplit extends AssignedIdEntity {

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "expense_id", nullable = false, updatable = false)
	private UUID expenseId;

	@Column(name = "member_id", nullable = false, updatable = false)
	private UUID memberId;

	@Column(name = "share_cents", nullable = false)
	private long shareCents;

	@Column(name = "input_cents")
	private Long inputCents;

	@Column(name = "input_basis_points")
	private Long inputBasisPoints;

	protected ExpenseSplit() {
	}

	ExpenseSplit(UUID id, UUID bandId, UUID expenseId, SplitCalculator.Share share) {
		super(id);
		this.bandId = bandId;
		this.expenseId = expenseId;
		this.memberId = share.memberId();
		this.shareCents = share.shareCents();
		this.inputCents = share.inputCents();
		this.inputBasisPoints = share.inputBasisPoints();
	}

	public UUID getExpenseId() {
		return expenseId;
	}

	public UUID getMemberId() {
		return memberId;
	}

	public long getShareCents() {
		return shareCents;
	}

	public Long getInputCents() {
		return inputCents;
	}

	public Long getInputBasisPoints() {
		return inputBasisPoints;
	}

	SplitCalculator.Share toShare() {
		return new SplitCalculator.Share(memberId, shareCents, inputCents, inputBasisPoints);
	}

}
