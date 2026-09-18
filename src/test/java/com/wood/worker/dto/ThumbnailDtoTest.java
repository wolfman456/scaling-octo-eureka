package com.wood.worker.dto;

import com.wood.worker.model.Article;
import com.wood.worker.model.GalleryItem;
import com.wood.worker.model.MediaAsset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThumbnailDtoTest {

    private static MediaAsset media(String storedName, String thumbnailName) {
        MediaAsset media = new MediaAsset();
        media.setAssetType(MediaAsset.AssetType.IMAGE);
        media.setStoredName(storedName);
        media.setThumbnailName(thumbnailName);
        media.setContentType("image/jpeg");
        media.setSortOrder(0);
        return media;
    }

    @Test
    void mediaAssetDtoExposesThumbnailUrl() {
        assertEquals("/uploads/a_thumb.jpg", MediaAssetDto.from(media("a.jpg", "a_thumb.jpg")).thumbnailUrl());
        assertNull(MediaAssetDto.from(media("a.png", null)).thumbnailUrl());
    }

    @Test
    void galleryItemDtoAlignsThumbnailList() {
        GalleryItem item = new GalleryItem();
        item.setTitle("Shelf");
        item.getMedia().add(media("a.jpg", "a_thumb.jpg"));
        item.getMedia().add(media("b.png", null));

        GalleryItemDto dto = GalleryItemDto.from(item);

        assertEquals(java.util.List.of("/uploads/a.jpg", "/uploads/b.png"), dto.images());
        assertEquals(java.util.Arrays.asList("/uploads/a_thumb.jpg", null), dto.thumbnails());
    }

    @Test
    void articleDtoExposesFeaturedThumbnail() {
        Article article = new Article();
        article.setTitle("Post");
        article.setSlug("post");
        article.setFeaturedMedia(media("hero.jpg", "hero_thumb.jpg"));
        article.getMedia().add(media("hero.jpg", "hero_thumb.jpg"));

        ArticleDto dto = ArticleDto.from(article);

        assertEquals("/uploads/hero.jpg", dto.featuredImage());
        assertEquals("/uploads/hero_thumb.jpg", dto.featuredThumbnail());
        assertEquals(java.util.List.of("/uploads/hero_thumb.jpg"), dto.thumbnails());
    }

    @Test
    void articleDtoHandlesFeaturedWithoutThumbnail() {
        Article article = new Article();
        article.setTitle("Post");
        article.setSlug("post");
        article.setFeaturedMedia(media("hero.png", null));

        ArticleDto dto = ArticleDto.from(article);

        assertEquals("/uploads/hero.png", dto.featuredImage());
        assertNull(dto.featuredThumbnail());
    }

    @Test
    void articleDtoHandlesMissingFeaturedMedia() {
        Article article = new Article();
        article.setTitle("Draft");
        article.setSlug("draft");

        ArticleDto dto = ArticleDto.from(article);

        assertNull(dto.featuredImage());
        assertNull(dto.featuredThumbnail());
        assertEquals(java.util.List.of(), dto.thumbnails());
    }

    @Test
    void articleSummaryDtoExposesFeaturedThumbnail() {
        Article article = new Article();
        article.setTitle("Post");
        article.setSlug("post");
        article.setFeaturedMedia(media("hero.jpg", "hero_thumb.jpg"));

        ArticleSummaryDto dto = ArticleSummaryDto.from(article);

        assertEquals("/uploads/hero_thumb.jpg", dto.featuredThumbnail());
    }

    @Test
    void articleSummaryDtoHandlesFeaturedWithoutThumbnail() {
        Article article = new Article();
        article.setTitle("Post");
        article.setSlug("post");
        article.setFeaturedMedia(media("hero.png", null));
        article.getMedia().add(media("hero.png", null));

        ArticleSummaryDto dto = ArticleSummaryDto.from(article);

        assertEquals("/uploads/hero.png", dto.featuredImage());
        assertNull(dto.featuredThumbnail());
        assertEquals(java.util.Collections.singletonList(null), dto.thumbnails());
    }

    @Test
    void articleSummaryDtoHandlesMissingFeaturedMedia() {
        Article article = new Article();
        article.setTitle("Draft");
        article.setSlug("draft");

        ArticleSummaryDto dto = ArticleSummaryDto.from(article);

        assertNull(dto.featuredImage());
        assertNull(dto.featuredThumbnail());
        assertEquals(java.util.List.of(), dto.thumbnails());
    }
}
