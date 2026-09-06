package com.wood.worker.dto;

import com.wood.worker.model.GalleryItem;

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
        List<String> images) {

    public static GalleryItemDto from(GalleryItem item) {
        List<String> images = item.getMedia().stream()
                .map(media -> "/uploads/" + media.getStoredName())
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
                images);
    }
}