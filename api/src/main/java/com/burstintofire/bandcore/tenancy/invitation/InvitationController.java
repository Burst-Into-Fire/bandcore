package com.burstintofire.bandcore.tenancy.invitation;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.tenancy.Role;

/** Invitations of the current band (admins). */
@RestController
@RequestMapping("/invitations")
@Tag(name = "Invitations")
class InvitationController {

	private final InvitationService invitations;

	private final Clock clock;

	InvitationController(InvitationService invitations, Clock clock) {
		this.invitations = invitations;
		this.clock = clock;
	}

	@GetMapping
	@Operation(operationId = "listInvitations", summary = "Invitations of the current band, newest first (admins)")
	List<InvitationResponse> list() {
		Instant now = clock.instant();
		return invitations.list().stream().map(invitation -> InvitationResponse.of(invitation, now)).toList();
	}

	@PostMapping
	@Operation(operationId = "createInvitation", summary = "Invite an email to the band with a role (admins)",
			description = "Sends the invitation email. 409 ALREADY_A_MEMBER when the email already has access, "
					+ "409 INVITATION_PENDING when a pending invitation exists (send it again instead).")
	ResponseEntity<InvitationResponse> create(@Valid @RequestBody CreateInvitationRequest request) {
		Invitation invitation = invitations.invite(request.email(), request.role(), request.memberId());
		return ResponseEntity.created(URI.create("/api/invitations/" + invitation.getId()))
			.body(InvitationResponse.of(invitation, clock.instant()));
	}

	@PostMapping("/{invitationId}/resend")
	@Operation(operationId = "resendInvitation",
			summary = "Send a pending or expired invitation again with a new link (admins)",
			description = "The previous link stops working and the invitation is valid for 7 more days.")
	InvitationResponse resend(@PathVariable UUID invitationId) {
		return InvitationResponse.of(invitations.resend(invitationId), clock.instant());
	}

	@DeleteMapping("/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "revokeInvitation", summary = "Revoke a pending invitation (admins)")
	void revoke(@PathVariable UUID invitationId) {
		invitations.revoke(invitationId);
	}

	record CreateInvitationRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = 254) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Role role,
			@Schema(description = "The member (integrante) to link to the account that accepts") UUID memberId) {
	}

	record InvitationResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role, UUID memberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Invitation.State status,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

		static InvitationResponse of(Invitation invitation, Instant now) {
			return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
					invitation.getMemberId(), invitation.state(now), invitation.getExpiresAt(),
					invitation.getCreatedAt());
		}

	}

}
