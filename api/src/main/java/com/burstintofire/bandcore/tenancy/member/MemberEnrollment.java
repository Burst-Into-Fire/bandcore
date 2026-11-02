package com.burstintofire.bandcore.tenancy.member;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.shared.id.IdGenerator;

/**
 * Gives an account that just joined a band its member record. Called by the invitation
 * acceptance, inside its transaction and RLS context; not an admin action, so no permission.
 */
@Component
public class MemberEnrollment {

	private final MemberRepository members;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	MemberEnrollment(MemberRepository members, AuditRecorder audit, IdGenerator ids, Clock clock) {
		this.members = members;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	/**
	 * In order: keep the account's existing member (reactivated if inactive); link the invited
	 * member when it has no account yet; create a member when {@code createWhenMissing}; otherwise
	 * leave the account without a member (for example a read-only manager).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Member> enroll(UUID bandId, UUID userId, String displayName, UUID invitedMemberId,
			boolean createWhenMissing) {
		Instant now = clock.instant();
		Optional<Member> existing = members.findByBandIdAndUserId(bandId, userId);
		if (existing.isPresent()) {
			Member member = existing.get();
			if (!member.isActive()) {
				member.activate(now);
				audit.record("member.activated", "member", member.getId(), Map.of());
			}
			return existing;
		}
		if (invitedMemberId != null) {
			Optional<Member> invited = members.findByBandIdAndId(bandId, invitedMemberId)
				.filter(member -> member.getUserId() == null);
			if (invited.isPresent()) {
				Member member = invited.get();
				member.linkTo(userId, now);
				if (!member.isActive()) {
					member.activate(now);
				}
				members.flush();
				audit.record("member.linked", "member", member.getId(),
						Map.of("user_id", AuditRecorder.change(null, userId.toString())));
				return invited;
			}
		}
		if (!createWhenMissing) {
			return Optional.empty();
		}
		Member member = members.saveAndFlush(new Member(ids.newId(), bandId, displayName, userId, now));
		audit.record("member.created", "member", member.getId(),
				Map.of("display_name", member.getDisplayName(), "user_id", userId.toString()));
		return Optional.of(member);
	}

}
