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
        String featuredThumbnail,
        Long featuredMediaId,
        List<String> images,
        List<String> thumbnails,
        List<Long> mediaIds) {

    public static ArticleDto from(Article article) {
        List<String> images = article.getMedia().stream()
                .map(media -> "/uploads/" + media.getStoredName())
                .toList();
        List<String> thumbnails = article.getMedia().stream()
                .map(media -> media.getThumbnailName() == null ? null : "/uploads/" + media.getThumbnailName())
                .toList();
        List<Long> mediaIds = article.getMedia().stream()
                .map(MediaAsset::getId)
                .toList();
        MediaAsset featured = article.getFeaturedMedia();
        return new ArticleDto(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getBodyMd(),
                article.getPublished(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                featured == null ? null : "/uploads/" + featured.getStoredName(),
                featured == null || featured.getThumbnailName() == null
                        ? null
                        : "/uploads/" + featured.getThumbnailName(),
                featured == null ? null : featured.getId(),
                images,
                thumbnails,
                mediaIds);
    }
}