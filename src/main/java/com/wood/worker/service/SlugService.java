package com.wood.worker.service;

import com.wood.worker.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SlugService {

    private final ArticleRepository articles;

    public SlugService(ArticleRepository articles) {
        this.articles = articles;
    }

    public String uniqueSlug(String base) {
        String source = base == null || base.isBlank() ? "article" : base;
        String slug = source.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            slug = "article";
        }
        String candidate = slug;
        int suffix = 2;
        while (articles.existsBySlug(candidate)) {
            candidate = slug + "-" + suffix++;
        }
        return candidate;
    }
}