package com.burstintofire.bandcore.finance.settlement;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.web.PageResponse;

/** Settlement payments between members of the current band, in cents of their currency. */
@RestController
@RequestMapping("/settlements")
@Tag(name = "Settlements")
class SettlementController {

	static final int MAX_PAGE = 10_000;

	static final int MAX_SIZE = 100;

	private final Settlements settlements;

	SettlementController(Settlements settlements) {
		this.settlements = settlements;
	}

	@GetMapping
	@Operation(operationId = "listSettlements", summary = "Settlements of the current band, newest first",
			description = "Deleted settlements are not listed. Pages start at 0.")
	PageResponse<SettlementResponse> list(
			@Parameter(description = "A member who sent or received") @RequestParam(required = false) UUID memberId,
			@RequestParam(required = false) Currency currency,
			@Parameter(description = "yyyy-MM, by payment date", schema = @Schema(type = "string", example = "2026-11"))
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(MAX_SIZE) int size) {
		return PageResponse.of(settlements.list(new Settlements.Filter(memberId, currency, month), page, size),
				SettlementResponse::of);
	}

	@GetMapping("/{settlementId}")
	@Operation(operationId = "getSettlement", summary = "One settlement of the current band")
	SettlementResponse get(@PathVariable UUID settlementId) {
		return SettlementResponse.of(settlements.get(settlementId));
	}

	@PostMapping
	@Operation(operationId = "createSettlement", summary = "Record that one member paid another",
			description = "Any member with write access records settlements between any two members, including "
					+ "members who left. 422 SETTLEMENT_SAME_MEMBER when both are the same member. Without paidOn, "
					+ "it is today in the band's time zone.")
	ResponseEntity<SettlementResponse> create(@Valid @RequestBody CreateSettlementRequest request) {
		Settlements.SettlementDetails created = settlements.create(new Settlements.NewSettlement(request.fromMemberId(),
				request.toMemberId(), request.currency(), request.amountCents(), request.paidOn(), request.note()));
		return ResponseEntity.created(URI.create("/api/settlements/" + created.settlement().getId()))
			.body(SettlementResponse.of(created));
	}

	@PatchMapping("/{settlementId}")
	@Operation(operationId = "updateSettlement",
			summary = "Change a settlement (recorded by the user or between their member and someone; admins any)",
			description = "Send the version that was read: 409 VERSION_CONFLICT when it changed meanwhile. Omitted "
					+ "fields are left unchanged; an empty note clears it.")
	SettlementResponse update(@PathVariable UUID settlementId, @Valid @RequestBody UpdateSettlementRequest request) {
		return SettlementResponse.of(settlements.update(settlementId,
				new Settlements.SettlementChanges(request.version(), request.fromMemberId(), request.toMemberId(),
						request.currency(), request.amountCents(), request.paidOn(), request.note())));
	}

	@DeleteMapping("/{settlementId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "deleteSettlement", summary = "Delete a settlement (same rule as changing it)",
			description = "It leaves lists and balances and is kept for the audit trail.")
	void delete(@PathVariable UUID settlementId,
			@Parameter(description = "The version that was read; 409 VERSION_CONFLICT when it changed")
			@RequestParam(required = false) Long version) {
		settlements.delete(settlementId, version);
	}

	record CreateSettlementRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Who paid") @NotNull UUID fromMemberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Who received") @NotNull UUID toMemberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive @Max(
					Settlement.MAX_AMOUNT_CENTS) Long amountCents,
			@Schema(description = "Defaults to today in the band's time zone") LocalDate paidOn,
			@Size(max = Settlement.MAX_NOTE_LENGTH) String note) {
	}

	record UpdateSettlementRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The version that was read") @NotNull @PositiveOrZero Long version,
			UUID fromMemberId, UUID toMemberId, Currency currency,
			@Positive @Max(Settlement.MAX_AMOUNT_CENTS) Long amountCents, LocalDate paidOn,
			@Schema(description = "Empty clears it") @Size(max = Settlement.MAX_NOTE_LENGTH) String note) {
	}

	record SettlementAccessResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean edit,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean delete) {
	}

	record SettlementResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID fromMemberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID toMemberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate paidOn, String note,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID createdBy,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Send it back when changing the settlement") long version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "What the current user may do with this settlement") SettlementAccessResponse access,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

		static SettlementResponse of(Settlements.SettlementDetails details) {
			Settlement settlement = details.settlement();
			return new SettlementResponse(settlement.getId(), settlement.getFromMemberId(), settlement.getToMemberId(),
					settlement.getCurrency(), settlement.getAmountCents(), settlement.getPaidOn(), settlement.getNote(),
					settlement.getCreatedBy(), settlement.getVersion(),
					new SettlementAccessResponse(details.access().edit(), details.access().delete()), settlement.getCreatedAt());
		}

	}

}
