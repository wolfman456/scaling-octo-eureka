package com.wood.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GalleryItemForm(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be 200 characters or fewer")
        String title,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,
        Long categoryId,
        Integer sortOrder,
        Boolean published,
        List<Long> mediaIds) {
}