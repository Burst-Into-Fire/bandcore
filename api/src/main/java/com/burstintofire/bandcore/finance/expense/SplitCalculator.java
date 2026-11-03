package com.burstintofire.bandcore.finance.expense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.burstintofire.bandcore.shared.money.Allocation;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Turns a split request into shares that add up exactly to the expense amount (RN01). Members
 * are ordered by id as text (the database's uuid order) before allocating, so the extra cents
 * always go to the same members; the web app sorts the same way.
 */
final class SplitCalculator {

	/** One member of a split request; {@code amountCents} or {@code basisPoints} per the mode. */
	record ShareInput(UUID memberId, Long amountCents, Long basisPoints) {
	}

	/** A computed share, with the input kept so the split can be edited later. */
	record Share(UUID memberId, long shareCents, Long inputCents, Long inputBasisPoints) {
	}

	static final Comparator<UUID> MEMBER_ORDER = Comparator.comparing(UUID::toString);

	private SplitCalculator() {
	}

	static List<Share> compute(SplitMode mode, long totalCents, List<ShareInput> inputs) {
		List<ShareInput> sorted = validated(mode, inputs);
		List<Share> shares = new ArrayList<>(sorted.size());
		switch (mode) {
			case EQUAL -> {
				long[] parts = Allocation.equal(totalCents, sorted.size());
				for (int i = 0; i < parts.length; i++) {
					shares.add(new Share(sorted.get(i).memberId(), parts[i], null, null));
				}
			}
			case CUSTOM_AMOUNTS -> {
				long sum = 0;
				for (ShareInput input : sorted) {
					sum = Math.addExact(sum, input.amountCents());
					shares.add(new Share(input.memberId(), input.amountCents(), input.amountCents(), null));
				}
				if (sum != totalCents) {
					throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SPLIT_SUM_MISMATCH,
							"The amounts add up to %d cents, not %d.".formatted(sum, totalCents), "split")
						.withProperty("differenceCents", totalCents - sum);
				}
			}
			case CUSTOM_PERCENTAGES -> {
				long[] weights = new long[sorted.size()];
				long sum = 0;
				for (int i = 0; i < weights.length; i++) {
					weights[i] = sorted.get(i).basisPoints();
					sum += weights[i];
				}
				if (sum != Allocation.BASIS_POINTS) {
					throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SPLIT_PERCENT_SUM,
							"The percentages add up to %d basis points, not 10000.".formatted(sum), "split")
						.withProperty("differenceBasisPoints", Allocation.BASIS_POINTS - sum);
				}
				long[] parts = Allocation.byWeights(totalCents, weights);
				for (int i = 0; i < parts.length; i++) {
					shares.add(new Share(sorted.get(i).memberId(), parts[i], null, weights[i]));
				}
			}
		}
		return List.copyOf(shares);
	}

	/** Rebuilds the inputs of saved shares, for recomputing them when the amount changes. */
	static List<ShareInput> inputsOf(List<Share> shares) {
		return shares.stream()
			.map(share -> new ShareInput(share.memberId(), share.inputCents(), share.inputBasisPoints()))
			.toList();
	}

	private static List<ShareInput> validated(SplitMode mode, List<ShareInput> inputs) {
		if (inputs.isEmpty()) {
			throw invalid("Choose at least one member to share the expense.");
		}
		Set<UUID> seen = new HashSet<>();
		for (ShareInput input : inputs) {
			if (input.memberId() == null || !seen.add(input.memberId())) {
				throw invalid("Each member can appear only once in a split.");
			}
			if (mode == SplitMode.CUSTOM_AMOUNTS && (input.amountCents() == null || input.amountCents() < 0)) {
				throw invalid("Give each member an amount of zero or more.");
			}
			if (mode == SplitMode.CUSTOM_PERCENTAGES && (input.basisPoints() == null || input.basisPoints() < 0
					|| input.basisPoints() > Allocation.BASIS_POINTS)) {
				throw invalid("Give each member a percentage between 0 and 100.");
			}
		}
		return inputs.stream().sorted(Comparator.comparing(ShareInput::memberId, MEMBER_ORDER)).toList();
	}

	private static ApiException invalid(String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SPLIT_INVALID, detail, "split");
	}

}
