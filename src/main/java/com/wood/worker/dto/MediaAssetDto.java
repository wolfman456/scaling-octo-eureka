package com.wood.worker.dto;

import com.wood.worker.model.MediaAsset;

import java.time.Instant;

public record MediaAssetDto(
        Long id,
        String assetType,
        String url,
        String thumbnailUrl,
        String contentType,
        Long sizeBytes,
        Instant uploadedAt) {

    public static MediaAssetDto from(MediaAsset asset) {
        String thumbnailUrl = asset.getThumbnailName() == null
                ? null
                : "/uploads/" + asset.getThumbnailName();
        return new MediaAssetDto(
                asset.getId(),
                asset.getAssetType().name(),
                "/uploads/" + asset.getStoredName(),
                thumbnailUrl,
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getUploadedAt());
    }
}