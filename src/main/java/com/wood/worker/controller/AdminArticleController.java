package com.wood.worker.controller;

import com.wood.worker.dto.ArticleDto;
import com.wood.worker.dto.ArticleForm;
import com.wood.worker.model.Article;
import com.wood.worker.repository.ArticleRepository;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.SlugService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final ArticleRepository articles;
    private final MediaAssetRepository media;
    private final SlugService slugService;

    public AdminArticleController(ArticleRepository articles,
                                  MediaAssetRepository media,
                                  SlugService slugService) {
        this.articles = articles;
        this.media = media;
        this.slugService = slugService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ArticleDto> list() {
        return articles.findAllByOrderByCreatedAtDesc().stream()
                .map(ArticleDto::from)
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ArticleDto> create(@RequestBody ArticleForm form) {
        Article article = new Article();
        apply(article, form);
        article.setSlug(resolveSlug(null, form.slug(), form.title()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ArticleDto.from(articles.save(article)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ArticleDto> update(@PathVariable Long id, @RequestBody ArticleForm form) {
        return articles.findById(id)
                .map(article -> {
                    apply(article, form);
                    article.setSlug(resolveSlug(article, form.slug(), form.title()));
                    article.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(ArticleDto.from(articles.save(article)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return articles.findById(id)
                .map(article -> {
                    articles.delete(article);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void apply(Article article, ArticleForm form) {
        article.setTitle(form.title());
        article.setBodyMd(form.bodyMd());
        article.setPublished(form.published());
        if (Boolean.TRUE.equals(form.published()) && article.getPublishedAt() == null) {
            article.setPublishedAt(Instant.now());
        }
        article.setFeaturedMedia(form.featuredMediaId() == null ? null : media.findById(form.featuredMediaId()).orElse(null));
        article.getMedia().clear();
        if (form.mediaIds() != null) {
            article.getMedia().addAll(media.findAllById(form.mediaIds()));
        }
    }

    private String resolveSlug(Article existing, String requested, String title) {
        String base = requested != null && !requested.isBlank() ? requested : title;
        if (existing != null && base.equals(existing.getSlug())) {
            return existing.getSlug();
        }
        return slugService.uniqueSlug(base);
    }
}