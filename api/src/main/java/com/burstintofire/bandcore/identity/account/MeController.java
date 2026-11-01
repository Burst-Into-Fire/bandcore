package com.burstintofire.bandcore.identity.account;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.identity.Accounts;
import com.burstintofire.bandcore.shared.security.AuthenticatedUser;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.AnyHostEndpoint;
import com.burstintofire.bandcore.shared.web.ApiException;

@RestController
@AnyHostEndpoint
@Tag(name = "Sessions")
class MeController {

	private final Accounts accounts;

	MeController(Accounts accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/me")
	@Operation(operationId = "getMe",
			summary = "The current user and, on a band host, their role and permissions in the band")
	MeResponse me(Authentication authentication) {
		AuthenticatedUser principal = CurrentUser.require();
		Accounts.Account account = accounts.find(principal.userId()).orElseThrow(ApiException::notFound);
		BandRole band = null;
		if (!principal.isCentral()) {
			List<String> permissions = authentication.getAuthorities()
				.stream()
				.map(GrantedAuthority::getAuthority)
				.sorted()
				.toList();
			band = new BandRole(principal.bandId(), TenantContextHolder.require().slug(), principal.role(), permissions);
		}
		return new MeResponse(account.id(), account.email(), account.displayName(), account.emailVerified(), band);
	}

	record MeResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emailVerified,
			@Schema(description = "Absent on the central host") BandRole band) {
	}

	record BandRole(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "ADMIN", "MEMBER", "READ_ONLY" }) String role,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> permissions) {
	}

}
