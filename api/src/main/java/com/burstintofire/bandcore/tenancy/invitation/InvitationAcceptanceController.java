package com.burstintofire.bandcore.tenancy.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.ratelimit.RateLimiter;
import com.burstintofire.bandcore.shared.web.CentralEndpoint;
import com.burstintofire.bandcore.shared.web.ClientAddresses;
import com.burstintofire.bandcore.shared.web.PublicEndpoint;
import com.burstintofire.bandcore.tenancy.Role;

/**
 * The invitee's side, on the central host. The token travels in the body (the link carries it
 * in the URL fragment), never in a URL the server logs.
 */
@RestController
@RequestMapping("/invitations")
@CentralEndpoint
@Tag(name = "Invitations")
class InvitationAcceptanceController {

	private final InvitationAcceptance acceptance;

	private final ClientAddresses clients;

	private final RateLimiter.RateLimit previewsPerIp;

	private final RateLimiter.RateLimit acceptancesPerIp;

	InvitationAcceptanceController(InvitationAcceptance acceptance, ClientAddresses clients, RateLimiter limiter) {
		this.acceptance = acceptance;
		this.clients = clients;
		this.previewsPerIp = limiter.limit("invitation-preview-ip");
		this.acceptancesPerIp = limiter.limit("invitation-accept-ip");
	}

	@PostMapping("/preview")
	@PublicEndpoint("the invitee sees the band and role before logging in or signing up")
	@Operation(operationId = "previewInvitation", summary = "Band, email and role of an invitation link",
			description = "Unknown, used, revoked and expired tokens answer 400 TOKEN_INVALID.")
	PreviewResponse preview(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
		previewsPerIp.consume(clients.clientOf(http));
		InvitationAcceptance.Preview preview = acceptance.preview(request.token());
		return new PreviewResponse(preview.bandName(), preview.bandSlug(), preview.email(), preview.role());
	}

	@PostMapping("/accept")
	@Operation(operationId = "acceptInvitation", summary = "Join the band of an invitation with the logged-in account",
			description = "The account's verified email must be the invited one (403 INVITATION_EMAIL_MISMATCH).")
	AcceptedResponse accept(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
		acceptancesPerIp.consume(clients.clientOf(http));
		InvitationAcceptance.Accepted accepted = acceptance.accept(request.token());
		return new AcceptedResponse(accepted.bandSlug(), accepted.bandUrl(), accepted.role());
	}

	record TokenRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String token) {

		@Override
		public String toString() {
			return "TokenRequest[]";
		}

	}

	record PreviewResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bandName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bandSlug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role) {
	}

	record AcceptedResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bandSlug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bandUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role) {
	}

}
