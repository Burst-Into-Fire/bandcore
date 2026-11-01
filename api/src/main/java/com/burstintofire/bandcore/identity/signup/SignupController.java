package com.burstintofire.bandcore.identity.signup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.identity.user.Emails;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.shared.web.CentralEndpoint;
import com.burstintofire.bandcore.shared.web.PublicEndpoint;

@RestController
@RequestMapping("/auth")
@CentralEndpoint
@PublicEndpoint("accounts are created and verified before anyone can log in")
@Tag(name = "Accounts")
class SignupController {

	private final SignupService signups;

	SignupController(SignupService signups) {
		this.signups = signups;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(operationId = "signUp", summary = "Create an account and send the verification email",
			description = "Always answers 202 for valid input, whether or not the email already has an account.")
	void signUp(@Valid @RequestBody SignupRequest request) {
		try {
			signups.signUp(request.email(), request.password(), request.displayName());
		}
		catch (DataIntegrityViolationException concurrentSignup) {
			// Another request created the same account a moment ago: answer as for an existing account.
		}
	}

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "verifyEmail", summary = "Confirm an email with the token from the verification link")
	void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		signups.verify(request.token());
	}

	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(operationId = "resendVerification", summary = "Send a new verification link",
			description = "Always answers 202, whether or not the email has an unverified account.")
	void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		signups.resendVerification(request.email());
	}

	record SignupRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = Emails.MAX_LENGTH) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 128) @NotNull String password,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = User.MAX_DISPLAY_NAME_LENGTH) String displayName) {

		@Override
		public String toString() {
			return "SignupRequest[]";
		}

	}

	record VerifyEmailRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String token) {
	}

	record ResendVerificationRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = Emails.MAX_LENGTH) String email) {
	}

}
