package com.burstintofire.bandcore.finance.balance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.money.Currency;

/** Who owes whom in the current band. */
@RestController
@RequestMapping("/balances")
@Tag(name = "Balances")
class BalanceController {

	private final Balances balances;

	BalanceController(Balances balances) {
		this.balances = balances;
	}

	@GetMapping
	@Operation(operationId = "getBalances", summary = "Each member's balance, per currency",
			description = "Balance = paid expenses the member paid − their shares of paid expenses + settlements sent − "
					+ "settlements received. Positive: the member receives; negative: the member owes. Each "
					+ "currency's balances add up to zero; currencies are never mixed. Only currencies with "
					+ "movements are listed. Members who left the band appear while their balance is not zero.")
	BalancesResponse get(@Parameter(description = "Count only payments up to this date (inclusive)")
	@RequestParam(required = false) LocalDate asOf) {
		return new BalancesResponse(asOf,
				balances.current(asOf).stream().map(CurrencyBalancesResponse::of).toList());
	}

	record BalancesResponse(LocalDate asOf,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CurrencyBalancesResponse> currencies) {
	}

	record CurrencyBalancesResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Who receives first, then who is settled, then who owes") List<MemberBalanceResponse> members) {

		static CurrencyBalancesResponse of(BalanceCalculator.CurrencyBalances balances) {
			return new CurrencyBalancesResponse(balances.currency(),
					balances.members().stream().map(MemberBalanceResponse::of).toList());
		}

	}

	record MemberBalanceResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID memberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Positive: receives; negative: owes") long balanceCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BalanceCalculator.Direction direction,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Paid expenses this member paid") long paidCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "This member's shares of paid expenses") long shareCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sentCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long receivedCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {

		static MemberBalanceResponse of(BalanceCalculator.MemberBalance balance) {
			return new MemberBalanceResponse(balance.memberId(), balance.balanceCents(), balance.direction(),
					balance.paidCents(), balance.shareCents(), balance.sentCents(), balance.receivedCents(),
					balance.active());
		}

	}

}
