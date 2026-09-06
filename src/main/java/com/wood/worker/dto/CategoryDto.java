package com.wood.worker.dto;

import com.wood.worker.model.Category;

import java.time.Instant;

public record CategoryDto(
        Long id,
        String name,
        Integer sortOrder,
        Instant createdAt) {

    public static CategoryDto from(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getSortOrder(), category.getCreatedAt());
    }
}