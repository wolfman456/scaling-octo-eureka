package com.wood.worker.dto;

public record SiteSettingsDto(
        String siteTitle,
        Long backgroundMediaId,
        String backgroundImage,
        String contactEmail,
        String etsyUrl,
        String instagramUrl,
        String facebookUrl) {
}