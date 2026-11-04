package com.burstintofire.bandcore.finance.settlement;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.BandCalendar;
import com.burstintofire.bandcore.tenancy.BandMembers;

/**
 * Settlement payments between members of the current band (RF18). Everyone reads them; members
 * with write access record them between any two members, and change their own: recorded by them,
 * or with their member as one of the two parties. Admins change any. Members who left the band
 * can still settle: they are the ones most likely to owe. Deleted settlements are kept for the
 * audit trail and leave balances.
 */
@Service
public class Settlements {

	public record NewSettlement(UUID fromMemberId, UUID toMemberId, Currency currency, long amountCents,
			LocalDate paidOn, String note) {
	}

	/** A partial update: null leaves a field unchanged; an empty note clears it. */
	public record SettlementChanges(long version, UUID fromMemberId, UUID toMemberId, Currency currency,
			Long amountCents, LocalDate paidOn, String note) {
	}

	public record Filter(UUID memberId, Currency currency, YearMonth month) {
	}

	public record Access(boolean edit, boolean delete) {
	}

	public record SettlementDetails(Settlement settlement, Access access) {
	}

	private final SettlementRepository settlements;

	private final BandMembers members;

	private final BandCalendar calendar;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	Settlements(SettlementRepository settlements, BandMembers members, BandCalendar calendar, AuditRecorder audit,
			IdGenerator ids, Clock clock) {
		this.settlements = settlements;
		this.members = members;
		this.calendar = calendar;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public Page<SettlementDetails> list(Filter filter, int page, int size) {
		Ownership ownership = ownership();
		LocalDate from = filter.month() != null ? filter.month().atDay(1) : LocalDate.of(1, 1, 1);
		LocalDate to = filter.month() != null ? filter.month().atEndOfMonth() : LocalDate.of(9999, 12, 31);
		// Placeholders instead of nulls, so every parameter has a type.
		return settlements
			.search(bandId(), filter.memberId() == null,
					filter.memberId() != null ? filter.memberId() : new UUID(0, 0), filter.currency() == null,
					filter.currency() != null ? filter.currency() : Currency.BRL, from, to, PageRequest.of(page, size))
			.map(settlement -> details(settlement, ownership));
	}

	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public SettlementDetails get(UUID settlementId) {
		return details(find(settlementId), ownership());
	}

	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public SettlementDetails create(NewSettlement input) {
		Settlement settlement = new Settlement(ids.newId(), bandId(), CurrentUser.require().userId(), clock.instant());
		settlement.change(memberOfBand(input.fromMemberId()), memberOfBand(input.toMemberId()), input.currency(),
				input.amountCents(), input.paidOn() != null ? input.paidOn() : calendar.today(), blankToNull(input.note()));
		requireTwoMembers(settlement);
		settlements.saveAndFlush(settlement);
		audit.record("settlement.created", "settlement", settlement.getId(), settlement.snapshot());
		return details(settlement, ownership());
	}

	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public SettlementDetails update(UUID settlementId, SettlementChanges changes) {
		Settlement settlement = find(settlementId);
		Ownership ownership = ownership();
		requireOwnership(settlement, ownership);
		requireVersion(settlement, changes.version());
		Map<String, Object> before = settlement.snapshot();
		settlement.change(
				changes.fromMemberId() != null && !changes.fromMemberId().equals(settlement.getFromMemberId())
						? memberOfBand(changes.fromMemberId()) : settlement.getFromMemberId(),
				changes.toMemberId() != null && !changes.toMemberId().equals(settlement.getToMemberId())
						? memberOfBand(changes.toMemberId()) : settlement.getToMemberId(),
				changes.currency() != null ? changes.currency() : settlement.getCurrency(),
				changes.amountCents() != null ? changes.amountCents() : settlement.getAmountCents(),
				changes.paidOn() != null ? changes.paidOn() : settlement.getPaidOn(),
				changes.note() != null ? blankToNull(changes.note()) : settlement.getNote());
		requireTwoMembers(settlement);
		Map<String, Object> diff = AuditRecorder.diff(before, settlement.snapshot());
		if (!diff.isEmpty()) {
			settlement.touch(clock.instant());
			flush(settlement);
			audit.record("settlement.updated", "settlement", settlement.getId(), diff);
		}
		return details(settlement, ownership);
	}

	/** Kept for the audit trail; it leaves lists and balances. */
	@PreAuthorize(Permissions.CAN_WRITE_FINANCE)
	@Transactional
	public void delete(UUID settlementId, Long version) {
		Settlement settlement = find(settlementId);
		requireOwnership(settlement, ownership());
		if (version != null) {
			requireVersion(settlement, version);
		}
		settlement.softDelete(clock.instant());
		flush(settlement);
		audit.record("settlement.deleted", "settlement", settlementId, new LinkedHashMap<>(settlement.snapshot()));
	}

	private Settlement find(UUID settlementId) {
		return settlements.findByBandIdAndIdAndDeletedAtIsNull(bandId(), settlementId)
			.orElseThrow(ApiException::notFound);
	}

	/** Members who left may still settle; unknown and other bands' ids answer 404. */
	private UUID memberOfBand(UUID memberId) {
		if (!members.activeStatus(List.of(memberId)).containsKey(memberId)) {
			throw ApiException.notFound();
		}
		return memberId;
	}

	private static void requireTwoMembers(Settlement settlement) {
		if (settlement.getFromMemberId().equals(settlement.getToMemberId())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SETTLEMENT_SAME_MEMBER,
					"A settlement goes from one member to another.", "toMemberId");
		}
	}

	private Ownership ownership() {
		UUID userId = CurrentUser.require().userId();
		return new Ownership(userId, members.memberOf(userId), CurrentUser.hasPermission(Permissions.FINANCE_MANAGE),
				CurrentUser.hasPermission(Permissions.FINANCE_WRITE));
	}

	private static SettlementDetails details(Settlement settlement, Ownership ownership) {
		boolean owns = ownership.mayChange(settlement);
		return new SettlementDetails(settlement, new Access(owns, owns));
	}

	private static void requireOwnership(Settlement settlement, Ownership ownership) {
		if (!ownership.mayChange(settlement)) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
					"Only admins change settlements between other members.");
		}
	}

	private static void requireVersion(Settlement settlement, long version) {
		if (settlement.getVersion() != version) {
			throw versionConflict();
		}
	}

	private void flush(Settlement settlement) {
		try {
			settlements.saveAndFlush(settlement);
		}
		catch (ObjectOptimisticLockingFailureException concurrent) {
			throw versionConflict();
		}
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
				"Someone else changed this settlement. Reload it and try again.");
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

	/** Who the current user is, for "own settlements"; computed once per request. */
	private record Ownership(UUID userId, Optional<UUID> memberId, boolean manage, boolean write) {

		boolean mayChange(Settlement settlement) {
			if (manage) {
				return true;
			}
			if (!write) {
				return false;
			}
			return userId.equals(settlement.getCreatedBy()) || memberId
				.filter(member -> member.equals(settlement.getFromMemberId()) || member.equals(settlement.getToMemberId()))
				.isPresent();
		}

	}

}
