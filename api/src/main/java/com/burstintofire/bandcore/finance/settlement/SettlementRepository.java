package com.burstintofire.bandcore.finance.settlement;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS) and skips deleted settlements. */
public interface SettlementRepository extends TenantRepository<Settlement, UUID> {

	Optional<Settlement> findByBandIdAndIdAndDeletedAtIsNull(UUID bandId, UUID id);

	/**
	 * Newest first. Each filter is optional: a member (sent or received), a currency, and a range of
	 * payment dates.
	 */
	@Query(value = """
			SELECT s FROM Settlement s
			WHERE s.bandId = :bandId AND s.deletedAt IS NULL
			  AND (:anyMember = true OR s.fromMemberId = :memberId OR s.toMemberId = :memberId)
			  AND (:anyCurrency = true OR s.currency = :currency)
			  AND s.paidOn BETWEEN :from AND :to
			ORDER BY s.paidOn DESC, s.id DESC
			""", countQuery = """
			SELECT count(s) FROM Settlement s
			WHERE s.bandId = :bandId AND s.deletedAt IS NULL
			  AND (:anyMember = true OR s.fromMemberId = :memberId OR s.toMemberId = :memberId)
			  AND (:anyCurrency = true OR s.currency = :currency)
			  AND s.paidOn BETWEEN :from AND :to
			""")
	Page<Settlement> search(UUID bandId, boolean anyMember, UUID memberId, boolean anyCurrency, Currency currency,
			LocalDate from, LocalDate to, Pageable page);

}
