package com.wood.worker.dto;

public record GalleryItemForm(
        String title,
        String description,
        String category,
        Integer sortOrder,
        Boolean published) {
}