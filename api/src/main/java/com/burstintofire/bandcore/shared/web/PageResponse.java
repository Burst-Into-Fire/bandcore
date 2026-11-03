package com.burstintofire.bandcore.shared.web;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.data.domain.Page;

/**
 * One page of a list endpoint. Pages are numbered from 0; lists take {@code page} and
 * {@code size} query parameters, with a maximum size per endpoint.
 */
public record PageResponse<T>(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalItems,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

	public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}
