package com.burstintofire.bandcore.finance.balance;

import java.util.UUID;

import com.burstintofire.bandcore.shared.money.Currency;

/**
 * What moved for one member in one currency: expenses they paid, their shares of expenses, and
 * settlements they sent and received. All in cents.
 */
record MemberTotals(UUID memberId, Currency currency, long paidCents, long shareCents, long sentCents,
		long receivedCents) {

	/**
	 * RN02: positive means the band owes the member (they receive), negative that they owe.
	 * Settlements sent reduce what one owes; settlements received reduce what one is owed.
	 */
	long balanceCents() {
		long balance = Math.subtractExact(paidCents, shareCents);
		balance = Math.addExact(balance, sentCents);
		return Math.subtractExact(balance, receivedCents);
	}

	MemberTotals plus(MemberTotals other) {
		return new MemberTotals(memberId, currency, Math.addExact(paidCents, other.paidCents),
				Math.addExact(shareCents, other.shareCents), Math.addExact(sentCents, other.sentCents),
				Math.addExact(receivedCents, other.receivedCents));
	}

	static MemberTotals zero(UUID memberId, Currency currency) {
		return new MemberTotals(memberId, currency, 0, 0, 0, 0);
	}

}
