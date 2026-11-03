package com.burstintofire.bandcore.finance.category;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Expense categories of the current band. */
@RestController
@RequestMapping("/expense-categories")
@Tag(name = "Expense categories")
class ExpenseCategoryController {

	private static final String COLOR = "^(#[0-9a-fA-F]{6})?$";

	private final ExpenseCategories categories;

	ExpenseCategoryController(ExpenseCategories categories) {
		this.categories = categories;
	}

	@GetMapping
	@Operation(operationId = "listExpenseCategories",
			summary = "Categories of the current band by name, archived ones included")
	List<CategoryResponse> list() {
		return categories.list().stream().map(CategoryResponse::of).toList();
	}

	@PostMapping
	@Operation(operationId = "createExpenseCategory", summary = "Create a category (admins)",
			description = "409 CATEGORY_NAME_TAKEN when another category, archived or not, has the name.")
	ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
		ExpenseCategory category = categories.create(request.name(), request.color());
		return ResponseEntity.created(URI.create("/api/expense-categories/" + category.getId()))
			.body(CategoryResponse.of(category));
	}

	@PatchMapping("/{categoryId}")
	@Operation(operationId = "updateExpenseCategory", summary = "Rename or recolor a category (admins)",
			description = "Omitted fields are left unchanged; an empty color clears it.")
	CategoryResponse update(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
		return CategoryResponse.of(categories.update(categoryId, request.name(), request.color()));
	}

	@PostMapping("/{categoryId}/archive")
	@Operation(operationId = "archiveExpenseCategory",
			summary = "Archive a category (admins): existing expenses keep it, new ones cannot use it")
	CategoryResponse archive(@PathVariable UUID categoryId) {
		return CategoryResponse.of(categories.archive(categoryId));
	}

	@PostMapping("/{categoryId}/unarchive")
	@Operation(operationId = "unarchiveExpenseCategory", summary = "Bring an archived category back (admins)")
	CategoryResponse unarchive(@PathVariable UUID categoryId) {
		return CategoryResponse.of(categories.unarchive(categoryId));
	}

	@DeleteMapping("/{categoryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "deleteExpenseCategory", summary = "Delete a category no expense uses (admins)",
			description = "409 CATEGORY_IN_USE when expenses use it: archive it instead.")
	void delete(@PathVariable UUID categoryId) {
		categories.delete(categoryId);
	}

	record CreateCategoryRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(
					max = ExpenseCategory.MAX_NAME_LENGTH) String name,
			@Schema(description = "#rrggbb", example = "#ff6b5a") @Pattern(regexp = COLOR) String color) {
	}

	record UpdateCategoryRequest(
			@Size(min = 1, max = ExpenseCategory.MAX_NAME_LENGTH) @Pattern(regexp = ".*\\S.*") String name,
			@Schema(description = "#rrggbb; empty clears it") @Pattern(regexp = COLOR) String color) {
	}

	record CategoryResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "Set while a default category keeps its original name; show its translation")
			DefaultCategory defaultKey,
			String color,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean archived) {

		static CategoryResponse of(ExpenseCategory category) {
			return new CategoryResponse(category.getId(), category.getName(), category.getDefaultKey(),
					category.getColor(), category.isArchived());
		}

	}

}
