package com.burstintofire.bandcore.tenancy.member;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.membership.MembershipRepository;
import com.burstintofire.bandcore.tenancy.membership.Membership;

/**
 * Admins manage the current band's members (integrantes). A member who leaves is deactivated,
 * never deleted, once anything refers to them: expenses, splits and sales reference members with
 * foreign keys, so the database itself refuses to delete a member with history.
 */
@Service
public class MemberAdministration {

	public static final int MAX_INSTRUMENT_LENGTH = 40;

	private final MemberRepository members;

	private final MembershipRepository memberships;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	MemberAdministration(MemberRepository members, MembershipRepository memberships, AuditRecorder audit,
			IdGenerator ids, Clock clock) {
		this.members = members;
		this.memberships = memberships;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Member create(String displayName, String instrument) {
		Instant now = clock.instant();
		Member member = new Member(ids.newId(), bandId(), displayName.strip(), null, now);
		member.changeInstrument(blankToNull(instrument), now);
		members.saveAndFlush(member);
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("display_name", member.getDisplayName());
		changes.put("instrument", member.getInstrument());
		audit.record("member.created", "member", member.getId(), changes);
		return member;
	}

	/** Null leaves a field unchanged; an empty instrument clears it. */
	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Member update(UUID memberId, String displayName, String instrument) {
		Member member = find(memberId);
		Instant now = clock.instant();
		Map<String, Object> changes = new LinkedHashMap<>();
		if (displayName != null && !displayName.strip().equals(member.getDisplayName())) {
			changes.put("display_name", AuditRecorder.change(member.getDisplayName(), displayName.strip()));
			member.rename(displayName.strip(), now);
		}
		if (instrument != null && !Objects.equals(blankToNull(instrument), member.getInstrument())) {
			changes.put("instrument", AuditRecorder.change(member.getInstrument(), blankToNull(instrument)));
			member.changeInstrument(blankToNull(instrument), now);
		}
		if (!changes.isEmpty()) {
			audit.record("member.updated", "member", member.getId(), changes);
		}
		return member;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Member deactivate(UUID memberId) {
		Member member = find(memberId);
		if (member.isActive()) {
			member.deactivate(clock.instant());
			audit.record("member.deactivated", "member", member.getId(), Map.of());
		}
		return member;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Member activate(UUID memberId) {
		Member member = find(memberId);
		if (!member.isActive()) {
			member.activate(clock.instant());
			audit.record("member.activated", "member", member.getId(), Map.of());
		}
		return member;
	}

	/** Only a member without history can be deleted; otherwise 409 MEMBER_HAS_HISTORY. */
	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public void delete(UUID memberId) {
		Member member = find(memberId);
		try {
			members.delete(member);
			members.flush();
		}
		catch (DataIntegrityViolationException referenced) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.MEMBER_HAS_HISTORY,
					"This member has history. Deactivate them instead.");
		}
		audit.record("member.deleted", "member", memberId, Map.of("display_name", member.getDisplayName()));
	}

	/** Links the member to an account that has access (a membership) to this band. */
	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Member link(UUID memberId, UUID userId) {
		Member member = find(memberId);
		if (userId.equals(member.getUserId())) {
			return member;
		}
		UUID bandId = bandId();
		if (memberships.findByBandIdAndUserId(bandId, userId).filter(Membership::isActive).isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.USER_NOT_IN_BAND,
					"Only accounts with access to this band can be linked.", "userId");
		}
		if (members.findByBandIdAndUserId(bandId, userId).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ACCOUNT_ALREADY_LINKED,
					"This account is already linked to another member.", "userId");
		}
		UUID previous = member.getUserId();
		member.linkTo(userId, clock.instant());
		members.flush();
		audit.record("member.linked", "member", member.getId(),
				Map.of("user_id", AuditRecorder.change(previous == null ? null : previous.toString(), userId.toString())));
		return member;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Member unlink(UUID memberId) {
		Member member = find(memberId);
		if (member.getUserId() != null) {
			UUID previous = member.getUserId();
			member.linkTo(null, clock.instant());
			audit.record("member.unlinked", "member", member.getId(),
					Map.of("user_id", AuditRecorder.change(previous.toString(), null)));
		}
		return member;
	}

	private Member find(UUID memberId) {
		return members.findByBandIdAndId(bandId(), memberId).orElseThrow(ApiException::notFound);
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
