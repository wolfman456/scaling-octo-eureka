package com.wood.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record GalleryItemForm(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be 200 characters or fewer")
        String title,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,
        @Size(max = 500, message = "Etsy listing URL must be 500 characters or fewer")
        @Pattern(regexp = "^(?:https://(?:www[.])?etsy[.]com/listing/[0-9]+(?:[/?#]\\S*)?)?$",
                message = "Enter an HTTPS Etsy listing URL")
        String etsyListingUrl,
        Long categoryId,
        Integer sortOrder,
        Boolean published,
        List<Long> mediaIds) {
}