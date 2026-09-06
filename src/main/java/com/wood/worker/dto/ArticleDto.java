package com.wood.worker.dto;

import com.wood.worker.model.Article;
import com.wood.worker.model.MediaAsset;

import java.time.Instant;
import java.util.List;

public record ArticleDto(
        Long id,
        String title,
        String slug,
        String bodyMd,
        Boolean published,
        Instant publishedAt,
        Instant createdAt,
        String featuredImage,
        List<String> images) {

    public static ArticleDto from(Article article) {
        List<String> images = article.getMedia().stream()
                .map(media -> "/uploads/" + media.getStoredName())
                .toList();
        MediaAsset featured = article.getFeaturedMedia();
        String featuredImage = featured == null ? null : "/uploads/" + featured.getStoredName();
        return new ArticleDto(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getBodyMd(),
                article.getPublished(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                featuredImage,
                images);
    }
}