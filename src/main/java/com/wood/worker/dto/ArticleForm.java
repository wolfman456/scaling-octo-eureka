package com.wood.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ArticleForm(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be 200 characters or fewer")
        String title,
        @Size(max = 200, message = "Slug must be 200 characters or fewer")
        String slug,
        @Size(max = 20000, message = "Body must be 20000 characters or fewer")
        String bodyMd,
        Long featuredMediaId,
        Boolean published,
        List<Long> mediaIds) {
}