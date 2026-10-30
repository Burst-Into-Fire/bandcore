package com.burstintofire.bandcore.tenancy.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.tenancy.member.Member;
import com.burstintofire.bandcore.tenancy.member.MemberQueries;

/** Members (integrantes) of the current band. */
@RestController
@RequestMapping("/members")
@Tag(name = "Members")
class MemberController {

	private final MemberQueries members;

	MemberController(MemberQueries members) {
		this.members = members;
	}

	@GetMapping
	@Operation(operationId = "listMembers", summary = "Members of the current band")
	List<MemberResponse> list() {
		return members.list().stream().map(MemberResponse::of).toList();
	}

	@GetMapping("/{memberId}")
	@Operation(operationId = "getMember", summary = "One member of the current band")
	MemberResponse get(@PathVariable UUID memberId) {
		return MemberResponse.of(members.get(memberId));
	}

	record MemberResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName, String instrument,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether the member has a login") boolean linkedToAccount,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "ACTIVE", "INACTIVE" }) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant joinedAt) {

		static MemberResponse of(Member member) {
			return new MemberResponse(member.getId(), member.getDisplayName(), member.getInstrument(),
					member.getUserId() != null, member.getStatus().name(), member.getJoinedAt());
		}

	}

}
