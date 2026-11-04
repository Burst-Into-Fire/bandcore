package com.burstintofire.bandcore.finance.balance;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.tenancy.BandMembers;

/** Each member's running balance in the current band, per currency (RF17). */
@Service
public class Balances {

	private final BalanceLedger ledger;

	private final BandMembers members;

	Balances(BalanceLedger ledger, BandMembers members) {
		this.ledger = ledger;
		this.members = members;
	}

	/** @param asOf the last payment date that counts; null for everything */
	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public List<BalanceCalculator.CurrencyBalances> current(LocalDate asOf) {
		UUID bandId = TenantContextHolder.require().bandId();
		List<MemberTotals> totals = ledger.totals(bandId, asOf != null ? asOf : BalanceLedger.NO_LIMIT);
		List<UUID> activeMembers = members.activeMemberIds();
		Map<UUID, Boolean> status = new HashMap<>(
				members.activeStatus(totals.stream().map(MemberTotals::memberId).distinct().toList()));
		activeMembers.forEach(memberId -> status.put(memberId, true));
		return BalanceCalculator.balances(totals, status, activeMembers);
	}

	public record CurrencyPlan(Currency currency,
			List<SettlementPlanner.Transfer> transfers) {
	}

	/** Suggested transfers that settle every current balance, per currency (RF19). */
	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public List<CurrencyPlan> settlementPlan() {
		return current(null).stream()
			.map(currency -> new CurrencyPlan(currency.currency(), SettlementPlanner.plan(currency.members())))
			.filter(plan -> !plan.transfers().isEmpty())
			.toList();
	}

}
