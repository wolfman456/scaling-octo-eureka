package com.wood.worker.repository;

import com.wood.worker.model.GalleryItem;
import com.wood.worker.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {

    List<GalleryItem> findAllByPublishedTrueOrderBySortOrderAscCreatedAtDesc();

    long countByCategoryId(Long categoryId);

    @Query("select i from GalleryItem i join i.media m where m.id = :mediaId")
    List<GalleryItem> findByMediaId(Long mediaId);
}