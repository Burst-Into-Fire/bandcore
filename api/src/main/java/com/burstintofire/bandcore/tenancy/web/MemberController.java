package com.burstintofire.bandcore.tenancy.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.tenancy.member.Member;
import com.burstintofire.bandcore.tenancy.member.MemberAdministration;
import com.burstintofire.bandcore.tenancy.member.MemberQueries;

/** Members (integrantes) of the current band. */
@RestController
@RequestMapping("/members")
@Tag(name = "Members")
class MemberController {

	private final MemberQueries members;

	private final MemberAdministration administration;

	MemberController(MemberQueries members, MemberAdministration administration) {
		this.members = members;
		this.administration = administration;
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

	@PostMapping
	@Operation(operationId = "createMember", summary = "Add a member (admins)")
	ResponseEntity<MemberResponse> create(@Valid @RequestBody CreateMemberRequest request) {
		Member member = administration.create(request.displayName(), request.instrument());
		return ResponseEntity.created(URI.create("/api/members/" + member.getId())).body(MemberResponse.of(member));
	}

	@PatchMapping("/{memberId}")
	@Operation(operationId = "updateMember", summary = "Change a member's name or instrument (admins)",
			description = "Omitted fields are left unchanged; an empty instrument clears it.")
	MemberResponse update(@PathVariable UUID memberId, @Valid @RequestBody UpdateMemberRequest request) {
		return MemberResponse.of(administration.update(memberId, request.displayName(), request.instrument()));
	}

	@PostMapping("/{memberId}/deactivate")
	@Operation(operationId = "deactivateMember",
			summary = "Mark a member as having left the band (admins); history and balances are kept")
	MemberResponse deactivate(@PathVariable UUID memberId) {
		return MemberResponse.of(administration.deactivate(memberId));
	}

	@PostMapping("/{memberId}/activate")
	@Operation(operationId = "activateMember", summary = "Bring a member back (admins)")
	MemberResponse activate(@PathVariable UUID memberId) {
		return MemberResponse.of(administration.activate(memberId));
	}

	@DeleteMapping("/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "deleteMember", summary = "Delete a member without history (admins)",
			description = "A member with expenses, splits or sales answers 409 MEMBER_HAS_HISTORY: deactivate instead.")
	void delete(@PathVariable UUID memberId) {
		administration.delete(memberId);
	}

	@PutMapping("/{memberId}/account")
	@Operation(operationId = "linkMemberAccount",
			summary = "Link a member to an account with access to this band (admins)")
	MemberResponse link(@PathVariable UUID memberId, @Valid @RequestBody LinkAccountRequest request) {
		return MemberResponse.of(administration.link(memberId, request.userId()));
	}

	@DeleteMapping("/{memberId}/account")
	@Operation(operationId = "unlinkMemberAccount", summary = "Unlink a member from its account (admins)")
	MemberResponse unlink(@PathVariable UUID memberId) {
		return MemberResponse.of(administration.unlink(memberId));
	}

	record CreateMemberRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80) @NotBlank @Size(max = Member.MAX_DISPLAY_NAME_LENGTH) String displayName,
			@Schema(maxLength = 40, example = "Vocal") @Size(max = MemberAdministration.MAX_INSTRUMENT_LENGTH) String instrument) {
	}

	record UpdateMemberRequest(
			@Schema(maxLength = 80) @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = Member.MAX_DISPLAY_NAME_LENGTH) String displayName,
			@Schema(maxLength = 40) @Size(max = MemberAdministration.MAX_INSTRUMENT_LENGTH) String instrument) {
	}

	record LinkAccountRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID userId) {
	}

	record MemberResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName, String instrument,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether the member has a login") boolean linkedToAccount,
			@Schema(description = "The linked account, when there is one") UUID userId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "ACTIVE", "INACTIVE" }) String status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant joinedAt) {

		static MemberResponse of(Member member) {
			return new MemberResponse(member.getId(), member.getDisplayName(), member.getInstrument(),
					member.getUserId() != null, member.getUserId(), member.getStatus().name(), member.getJoinedAt());
		}

	}

}
