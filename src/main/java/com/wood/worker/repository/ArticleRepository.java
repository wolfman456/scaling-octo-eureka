package com.wood.worker.repository;

import com.wood.worker.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findAllByOrderByCreatedAtDesc();

    List<Article> findAllByPublishedTrueOrderByCreatedAtDesc();

    Optional<Article> findBySlugAndPublishedTrue(String slug);

    boolean existsBySlug(String slug);

    Optional<Article> findByFeaturedMediaId(Long mediaId);

    @Query("select a from Article a join a.media m where m.id = :mediaId")
    List<Article> findByMediaId(Long mediaId);
}