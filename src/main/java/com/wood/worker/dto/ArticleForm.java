package com.wood.worker.dto;

import java.util.List;

public record ArticleForm(
        String title,
        String slug,
        String bodyMd,
        Long featuredMediaId,
        Boolean published,
        List<Long> mediaIds) {
}