package com.burstintofire.bandcore.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void arithmeticKeepsTheCurrency() {
		Money ten = Money.of(1000, Currency.USD);

		assertThat(ten.plus(Money.of(1, Currency.USD))).isEqualTo(Money.of(1001, Currency.USD));
		assertThat(ten.minus(Money.of(1001, Currency.USD))).isEqualTo(Money.of(-1, Currency.USD));
		assertThat(ten.negate()).isEqualTo(Money.of(-1000, Currency.USD));
		assertThat(ten.times(3)).isEqualTo(Money.of(3000, Currency.USD));
		assertThat(Money.zero(Currency.BRL).isZero()).isTrue();
		assertThat(ten.isPositive()).isTrue();
		assertThat(ten.negate().isNegative()).isTrue();
	}

	@Test
	void differentCurrenciesAreNeverCombined() {
		assertThatThrownBy(() -> Money.of(100, Currency.BRL).plus(Money.of(100, Currency.USD)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("BRL")
			.hasMessageContaining("USD");
		assertThatThrownBy(() -> Money.of(100, Currency.BRL).minus(Money.of(100, Currency.USD)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void overflowThrowsInsteadOfWrappingAround() {
		Money max = Money.of(Long.MAX_VALUE, Currency.BRL);

		assertThatThrownBy(() -> max.plus(Money.of(1, Currency.BRL))).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> Money.of(Long.MIN_VALUE, Currency.BRL).minus(Money.of(1, Currency.BRL)))
			.isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> Money.of(Long.MIN_VALUE, Currency.BRL).negate())
			.isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> max.times(2)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void currencyIsRequired() {
		assertThatThrownBy(() -> Money.of(1, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void allocatesInTheSameCurrency() {
		assertThat(Money.of(100, Currency.USD).allocateByWeights(new long[] { 3333, 3333, 3334 }))
			.containsExactly(Money.of(33, Currency.USD), Money.of(33, Currency.USD), Money.of(34, Currency.USD));
	}

}
