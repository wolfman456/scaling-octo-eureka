package com.wood.worker.controller;

import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.ArticleRepository;
import com.wood.worker.repository.GalleryItemRepository;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping(value = "/api/page-meta", produces = MediaType.TEXT_HTML_VALUE)
@Transactional(readOnly = true)
public class PageMetadataController {

    private static final String ORIGIN = "https://6kidscrafts.com";
    private static final String SITE_NAME = "Six Kids Crafts";
    private static final String FALLBACK_IMAGE = "/apple-touch-icon.png";

    private final GalleryItemRepository pieces;
    private final ArticleRepository articles;

    public PageMetadataController(GalleryItemRepository pieces, ArticleRepository articles) {
        this.pieces = pieces;
        this.articles = articles;
    }

    @GetMapping("/gallery/{id}")
    public String gallery(@PathVariable long id) {
        return pieces.findById(id)
                .filter(piece -> Boolean.TRUE.equals(piece.getPublished()))
                .map(piece -> tags(piece.getTitle(), piece.getDescription(), "/gallery/" + id, "website",
                        piece.getMedia().stream()
                                .filter(media -> media.getAssetType() == MediaAsset.AssetType.IMAGE)
                                .findFirst()
                                .orElse(null)))
                .orElseGet(PageMetadataController::missing);
    }

    @GetMapping("/articles/{slug}")
    public String article(@PathVariable String slug) {
        return articles.findFirstBySlugAndPublishedTrue(slug)
                .map(article -> tags(article.getTitle(), article.getBodyMd(), "/articles/" + article.getSlug(),
                        "article", article.getFeaturedMedia()))
                .orElseGet(PageMetadataController::missing);
    }

    private static String tags(String title, String description, String path, String type, MediaAsset photo) {
        String fullTitle = escape(title + " · " + SITE_NAME);
        String summary = escape(summary(description));
        String canonical = escape(ORIGIN + path);
        String image = escape(ORIGIN + (photo == null ? FALLBACK_IMAGE : "/uploads/" + photo.getStoredName()));
        return """
                <title>%s</title>
                <link rel="canonical" href="%s">
                <meta name="description" content="%s">
                <meta property="og:site_name" content="Six Kids Crafts">
                <meta property="og:type" content="%s">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:url" content="%s">
                <meta property="og:image" content="%s">
                <meta name="twitter:card" content="summary_large_image">
                """.formatted(fullTitle, canonical, summary, type, fullTitle, summary, canonical, image);
    }

    private static String summary(String description) {
        String text = description == null ? "" : description.replaceAll("[#*_>`]", "").replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) {
            return "Handcrafted woodworking by " + SITE_NAME + ".";
        }
        return text.codePointCount(0, text.length()) <= 160
                ? text
                : text.substring(0, text.offsetByCodePoints(0, 157)).stripTrailing() + "…";
    }

    private static String missing() {
        return "<title>Page unavailable · Six Kids Crafts</title><meta name=\"robots\" content=\"noindex\">";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
