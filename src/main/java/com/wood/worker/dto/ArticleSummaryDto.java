package com.wood.worker.dto;

import com.wood.worker.model.Article;
import com.wood.worker.model.MediaAsset;

import java.time.Instant;
import java.util.List;

public record ArticleSummaryDto(
        Long id,
        String title,
        String slug,
        Instant publishedAt,
        String featuredImage,
        List<String> images) {

    public static ArticleSummaryDto from(Article article) {
        List<String> images = article.getMedia().stream()
                .map(media -> "/uploads/" + media.getStoredName())
                .toList();
        MediaAsset featured = article.getFeaturedMedia();
        String featuredImage = featured == null ? null : "/uploads/" + featured.getStoredName();
        return new ArticleSummaryDto(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getPublishedAt(),
                featuredImage,
                images);
    }
}