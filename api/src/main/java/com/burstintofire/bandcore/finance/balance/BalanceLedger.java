package com.burstintofire.bandcore.finance.balance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.money.Currency;

/**
 * Adds up, in one query, what moved for each member and currency: paid expenses they paid, their
 * shares of paid expenses, settlements they sent and received. Planned and deleted records do
 * not count. Filters by band explicitly, on top of row-level security.
 */
@Component
class BalanceLedger {

	/** Far enough to mean "no limit" without a nullable date parameter. */
	static final LocalDate NO_LIMIT = LocalDate.of(9999, 12, 31);

	private static final String TOTALS = """
			WITH ledger (member_id, currency, paid, share, sent, received) AS (
			    SELECT e.paid_by_member_id, e.currency, e.amount_cents, 0, 0, 0
			    FROM expenses e
			    WHERE e.band_id = ? AND e.status = 'PAID' AND e.deleted_at IS NULL AND e.paid_on <= ?
			  UNION ALL
			    SELECT s.member_id, e.currency, 0, s.share_cents, 0, 0
			    FROM expense_splits s
			    JOIN expenses e ON e.band_id = s.band_id AND e.id = s.expense_id
			    WHERE s.band_id = ? AND e.status = 'PAID' AND e.deleted_at IS NULL AND e.paid_on <= ?
			  UNION ALL
			    SELECT t.from_member_id, t.currency, 0, 0, t.amount_cents, 0
			    FROM settlements t
			    WHERE t.band_id = ? AND t.deleted_at IS NULL AND t.paid_on <= ?
			  UNION ALL
			    SELECT t.to_member_id, t.currency, 0, 0, 0, t.amount_cents
			    FROM settlements t
			    WHERE t.band_id = ? AND t.deleted_at IS NULL AND t.paid_on <= ?
			)
			SELECT member_id, currency, sum(paid), sum(share), sum(sent), sum(received)
			FROM ledger
			GROUP BY member_id, currency
			""";

	private final JdbcTemplate jdbc;

	BalanceLedger(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @param asOf the last day that counts (payment dates), or {@link #NO_LIMIT} */
	List<MemberTotals> totals(UUID bandId, LocalDate asOf) {
		return jdbc.query(TOTALS,
				(rs, row) -> new MemberTotals(rs.getObject(1, UUID.class), Currency.valueOf(rs.getString(2)),
						rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)),
				bandId, asOf, bandId, asOf, bandId, asOf, bandId, asOf);
	}

}
