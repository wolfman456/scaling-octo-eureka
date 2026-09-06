package com.wood.worker.dto;

import com.wood.worker.model.MediaAsset;

import java.time.Instant;

public record MediaAssetDto(
        Long id,
        String assetType,
        String url,
        String contentType,
        Long sizeBytes,
        Instant uploadedAt) {

    public static MediaAssetDto from(MediaAsset asset) {
        return new MediaAssetDto(
                asset.getId(),
                asset.getAssetType().name(),
                "/uploads/" + asset.getStoredName(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getUploadedAt());
    }
}