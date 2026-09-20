package com.wood.worker.dto;

import jakarta.validation.constraints.Size;

public record SettingsUpdateForm(
        @Size(max = 500, message = "Site title must be 500 characters or fewer")
        String siteTitle,
        String backgroundMediaId,
        @Size(max = 500, message = "Contact email must be 500 characters or fewer")
        String contactEmail,
        @Size(max = 500, message = "Etsy URL must be 500 characters or fewer")
        String etsyUrl,
        @Size(max = 500, message = "Instagram URL must be 500 characters or fewer")
        String instagramUrl,
        @Size(max = 500, message = "Facebook URL must be 500 characters or fewer")
        String facebookUrl) {
}