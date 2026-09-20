package com.wood.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryForm(
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must be 100 characters or fewer")
        String name,
        Integer sortOrder) {
}