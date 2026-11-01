package com.burstintofire.bandcore.identity.reset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.identity.user.Emails;
import com.burstintofire.bandcore.shared.web.CentralEndpoint;
import com.burstintofire.bandcore.shared.web.PublicEndpoint;

@RestController
@RequestMapping("/auth/password-reset")
@CentralEndpoint
@PublicEndpoint("a forgotten password is exactly when the user cannot log in")
@Tag(name = "Accounts")
class PasswordResetController {

	private final PasswordResetService resets;

	PasswordResetController(PasswordResetService resets) {
		this.resets = resets;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(operationId = "requestPasswordReset", summary = "Send a password reset link",
			description = "Always answers 202 for valid input, whether or not the email has an account.")
	void request(@Valid @RequestBody PasswordResetRequest request) {
		resets.request(request.email());
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "confirmPasswordReset",
			summary = "Set a new password with the token from the reset link",
			description = "Ends every session of the user, on every host.")
	void confirm(@Valid @RequestBody PasswordResetConfirmation request) {
		resets.reset(request.token(), request.password());
	}

	record PasswordResetRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = Emails.MAX_LENGTH) String email) {
	}

	record PasswordResetConfirmation(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String token,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 128) @NotNull String password) {

		@Override
		public String toString() {
			return "PasswordResetConfirmation[]";
		}

	}

}
