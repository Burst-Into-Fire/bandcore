package com.burstintofire.bandcore.tenancy.member;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.tenancy.BandMembers;

/** Answers other modules' questions about the current band's members. */
@Component
class MemberDirectory implements BandMembers {

	private final MemberRepository members;

	MemberDirectory(MemberRepository members) {
		this.members = members;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UUID> memberOf(UUID userId) {
		return members.findByBandIdAndUserId(bandId(), userId).map(Member::getId);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, Boolean> activeStatus(Collection<UUID> memberIds) {
		Map<UUID, Boolean> status = new LinkedHashMap<>();
		if (memberIds.isEmpty()) {
			return status;
		}
		for (Member member : members.findByBandIdAndIdIn(bandId(), memberIds)) {
			status.put(member.getId(), member.isActive());
		}
		return status;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> activeMemberIds() {
		return members.findByBandIdAndStatusOrderByIdAsc(bandId(), MemberStatus.ACTIVE)
			.stream()
			.map(Member::getId)
			.toList();
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}
