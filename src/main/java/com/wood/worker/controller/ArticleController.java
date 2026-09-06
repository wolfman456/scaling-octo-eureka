package com.wood.worker.controller;

import com.wood.worker.dto.ArticleDto;
import com.wood.worker.dto.ArticleSummaryDto;
import com.wood.worker.repository.ArticleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleRepository articles;

    public ArticleController(ArticleRepository articles) {
        this.articles = articles;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> list() {
        return articles.findAllByPublishedTrueOrderByCreatedAtDesc().stream()
                .map(ArticleSummaryDto::from)
                .toList();
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<ArticleDto> get(@PathVariable String slug) {
        return articles.findBySlugAndPublishedTrue(slug)
                .map(article -> ResponseEntity.ok(ArticleDto.from(article)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}