package com.burstintofire.bandcore.shared.money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An amount of money: integer cents and a currency (ADR-0006). Immutable; arithmetic throws
 * {@link ArithmeticException} on overflow and {@link IllegalArgumentException} when currencies
 * differ, because amounts in different currencies are never added together (RN08).
 *
 * <p>The API exchanges the two parts as {@code amountCents} and {@code currency}.
 */
public record Money(long cents, Currency currency) {

	public Money {
		Objects.requireNonNull(currency, "currency");
	}

	public static Money of(long cents, Currency currency) {
		return new Money(cents, currency);
	}

	public static Money zero(Currency currency) {
		return new Money(0, currency);
	}

	public Money plus(Money other) {
		return new Money(Math.addExact(cents, sameCurrency(other).cents), currency);
	}

	public Money minus(Money other) {
		return new Money(Math.subtractExact(cents, sameCurrency(other).cents), currency);
	}

	public Money negate() {
		return new Money(Math.negateExact(cents), currency);
	}

	public Money times(long factor) {
		return new Money(Math.multiplyExact(cents, factor), currency);
	}

	public boolean isPositive() {
		return cents > 0;
	}

	public boolean isNegative() {
		return cents < 0;
	}

	public boolean isZero() {
		return cents == 0;
	}

	/** Equal parts that add up to this amount; see {@link Allocation#equal}. */
	public List<Money> allocateEqually(int parts) {
		return wrap(Allocation.equal(cents, parts));
	}

	/** Parts proportional to the weights that add up to this amount; see {@link Allocation#byWeights}. */
	public List<Money> allocateByWeights(long[] weights) {
		return wrap(Allocation.byWeights(cents, weights));
	}

	private List<Money> wrap(long[] parts) {
		List<Money> result = new ArrayList<>(parts.length);
		for (long part : parts) {
			result.add(new Money(part, currency));
		}
		return List.copyOf(result);
	}

	private Money sameCurrency(Money other) {
		if (other.currency != currency) {
			throw new IllegalArgumentException("Cannot combine " + currency + " and " + other.currency);
		}
		return other;
	}

	@Override
	public String toString() {
		return currency + " " + cents + "¢";
	}

}
