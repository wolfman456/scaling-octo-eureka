package com.wood.worker.repository;

import com.wood.worker.model.GalleryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {

    List<GalleryItem> findAllByPublishedTrueOrderBySortOrderAscCreatedAtDesc();
}