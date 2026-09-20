package com.wood.worker.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record ArticleDraftRequest(
        @Size(max = 500, message = "Topic must be 500 characters or fewer")
        String topic,
        List<Long> mediaIds) {
}