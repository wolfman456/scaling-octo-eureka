package com.wood.worker.service;

import com.wood.worker.repository.ArticleRepository;
import com.wood.worker.repository.GalleryItemRepository;
import com.wood.worker.repository.SiteSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class MediaUsageService {

    private final GalleryItemRepository galleryItems;
    private final ArticleRepository articles;
    private final SiteSettingRepository settings;

    public MediaUsageService(GalleryItemRepository galleryItems,
                             ArticleRepository articles,
                             SiteSettingRepository settings) {
        this.galleryItems = galleryItems;
        this.articles = articles;
        this.settings = settings;
    }

    public boolean isInUse(Long mediaId) {
        if (!galleryItems.findByMediaId(mediaId).isEmpty()) {
            return true;
        }
        if (!articles.findByMediaId(mediaId).isEmpty() || articles.findByFeaturedMediaId(mediaId).isPresent()) {
            return true;
        }
        return settings.findById("background_media_id")
                .map(setting -> setting.getValue().equals(String.valueOf(mediaId)))
                .orElse(false);
    }
}