package com.wood.worker.dto;

import com.wood.worker.model.GalleryItem;
import com.wood.worker.model.MediaAsset;

import java.time.Instant;
import java.util.List;

public record GalleryItemDto(
        Long id,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        Integer sortOrder,
        Boolean published,
        Instant createdAt,
        List<String> images,
        List<Long> mediaIds) {

    public static GalleryItemDto from(GalleryItem item) {
        List<String> images = item.getMedia().stream()
                .map(media -> "/uploads/" + media.getStoredName())
                .toList();
        List<Long> mediaIds = item.getMedia().stream()
                .map(MediaAsset::getId)
                .toList();
        return new GalleryItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getName(),
                item.getSortOrder(),
                item.getPublished(),
                item.getCreatedAt(),
                images,
                mediaIds);
    }
}