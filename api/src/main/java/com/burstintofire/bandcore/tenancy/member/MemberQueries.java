package com.burstintofire.bandcore.tenancy.member;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;

/** Reads the current band's members. Writes arrive in M4-01. */
@Service
public class MemberQueries {

	private final MemberRepository members;

	MemberQueries(MemberRepository members) {
		this.members = members;
	}

	@PreAuthorize(Permissions.CAN_READ_MEMBERS)
	@Transactional(readOnly = true)
	public List<Member> list() {
		return members.findByBandIdOrderByDisplayNameAsc(TenantContextHolder.require().bandId());
	}

	/** Another band's member answers 404, exactly like a member that does not exist. */
	@PreAuthorize(Permissions.CAN_READ_MEMBERS)
	@Transactional(readOnly = true)
	public Member get(UUID memberId) {
		return members.findByBandIdAndId(TenantContextHolder.require().bandId(), memberId)
			.orElseThrow(ApiException::notFound);
	}

}
