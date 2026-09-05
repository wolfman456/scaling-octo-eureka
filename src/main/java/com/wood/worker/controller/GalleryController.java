package com.wood.worker.controller;

import com.wood.worker.dto.GalleryItemDto;
import com.wood.worker.model.GalleryItem;
import com.wood.worker.repository.GalleryItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gallery")
public class GalleryController {

    private final GalleryItemRepository repository;

    public GalleryController(GalleryItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public List<GalleryItemDto> list() {
        return repository.findAllByPublishedTrueOrderBySortOrderAscCreatedAtDesc()
                .stream()
                .map(GalleryItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ResponseEntity<GalleryItemDto> get(@PathVariable Long id) {
        return repository.findById(id)
                .filter(item -> Boolean.TRUE.equals(item.getPublished()))
                .map(item -> ResponseEntity.ok(GalleryItemDto.from(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}