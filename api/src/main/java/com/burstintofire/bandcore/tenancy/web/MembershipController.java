package com.burstintofire.bandcore.tenancy.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.identity.Accounts;
import com.burstintofire.bandcore.tenancy.Role;
import com.burstintofire.bandcore.tenancy.member.Member;
import com.burstintofire.bandcore.tenancy.member.MemberQueries;
import com.burstintofire.bandcore.tenancy.membership.Membership;
import com.burstintofire.bandcore.tenancy.membership.MembershipAdministration;

/** Who has access to the current band (accounts with a role), as opposed to members (integrantes). */
@RestController
@RequestMapping("/memberships")
@Tag(name = "Memberships")
class MembershipController {

	private final MembershipAdministration memberships;

	private final MemberQueries members;

	private final Accounts accounts;

	MembershipController(MembershipAdministration memberships, MemberQueries members, Accounts accounts) {
		this.memberships = memberships;
		this.members = members;
		this.accounts = accounts;
	}

	@GetMapping
	@Operation(operationId = "listMemberships", summary = "Accounts with access to the current band (admins)")
	List<MembershipResponse> list() {
		List<Membership> active = memberships.list();
		Map<UUID, Accounts.Account> byUser = accounts.findAll(active.stream().map(Membership::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(Accounts.Account::id, Function.identity()));
		Map<UUID, UUID> memberByUser = members.list()
			.stream()
			.filter(member -> member.getUserId() != null)
			.collect(Collectors.toMap(Member::getUserId, Member::getId));
		return active.stream()
			.map(membership -> MembershipResponse.of(membership, byUser.get(membership.getUserId()),
					memberByUser.get(membership.getUserId())))
			.sorted(Comparator.comparing(MembershipResponse::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	@PatchMapping("/{membershipId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "changeMembershipRole", summary = "Change someone's role (admins)",
			description = "409 LAST_ADMIN when it would leave the band without an admin.")
	void changeRole(@PathVariable UUID membershipId, @Valid @RequestBody ChangeRoleRequest request) {
		memberships.changeRole(membershipId, request.role());
	}

	@DeleteMapping("/{membershipId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "removeMembership", summary = "Remove someone's access to the band (admins)",
			description = "Their sessions in this band end at once; their member record and history stay. "
					+ "409 LAST_ADMIN for the last admin.")
	void remove(@PathVariable UUID membershipId) {
		memberships.remove(membershipId);
	}

	@PostMapping("/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "leaveBand", summary = "Leave the current band",
			description = "Ends the caller's sessions in this band. 409 LAST_ADMIN for the last admin.")
	void leave() {
		memberships.leave();
	}

	record ChangeRoleRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Role role) {
	}

	record MembershipResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
			@Schema(description = "The member (integrante) linked to this account, if any") UUID memberId) {

		static MembershipResponse of(Membership membership, Accounts.Account account, UUID memberId) {
			return new MembershipResponse(membership.getId(), membership.getUserId(),
					account == null ? "" : account.displayName(), account == null ? "" : account.email(),
					membership.getRole(), memberId);
		}

	}

}
