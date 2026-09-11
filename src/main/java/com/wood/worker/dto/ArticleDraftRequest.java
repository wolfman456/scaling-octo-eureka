package com.wood.worker.dto;

import java.util.List;

public record ArticleDraftRequest(
        String topic,
        List<Long> mediaIds) {
}