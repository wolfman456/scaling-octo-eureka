package com.wood.worker.dto;

public record SettingsUpdateForm(
        String siteTitle,
        Long backgroundMediaId,
        String contactEmail,
        String etsyUrl,
        String instagramUrl,
        String facebookUrl) {
}