package com.wood.worker.controller;

import com.wood.worker.dto.GalleryItemDto;
import com.wood.worker.dto.GalleryItemForm;
import com.wood.worker.model.GalleryItem;
import com.wood.worker.repository.CategoryRepository;
import com.wood.worker.repository.GalleryItemRepository;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.MediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/gallery")
public class AdminGalleryController {

    private final GalleryItemRepository repository;
    private final CategoryRepository categories;
    private final MediaAssetRepository media;
    private final MediaStorageService storageService;

    public AdminGalleryController(GalleryItemRepository repository,
                                  CategoryRepository categories,
                                  MediaAssetRepository media,
                                  MediaStorageService storageService) {
        this.repository = repository;
        this.categories = categories;
        this.media = media;
        this.storageService = storageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<GalleryItemDto> create(@RequestPart("item") GalleryItemForm form,
                                                 @RequestPart(value = "image", required = false) MultipartFile image) {
        GalleryItem item = new GalleryItem();
        apply(item, form);
        attachMedia(item, form);
        if (image != null && !image.isEmpty()) {
            item.getMedia().add(storageService.store(image, item.getMedia().size()));
        }
        GalleryItem saved = repository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(GalleryItemDto.from(saved));
    }

    @PutMapping(path = "/{id}", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<GalleryItemDto> update(@PathVariable Long id,
                                                 @RequestPart("item") GalleryItemForm form,
                                                 @RequestPart(value = "image", required = false) MultipartFile image) {
        return repository.findById(id)
                .map(item -> {
                    apply(item, form);
                    attachMedia(item, form);
                    if (image != null && !image.isEmpty()) {
                        item.getMedia().add(storageService.store(image, item.getMedia().size()));
                    }
                    item.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(GalleryItemDto.from(repository.save(item)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return repository.findById(id)
                .map(item -> {
                    repository.delete(item);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void apply(GalleryItem item, GalleryItemForm form) {
        item.setTitle(form.title());
        item.setDescription(form.description());
        item.setCategory(form.categoryId() == null ? null : categories.findById(form.categoryId()).orElse(null));
        item.setSortOrder(form.sortOrder());
        item.setPublished(form.published());
    }

    private void attachMedia(GalleryItem item, GalleryItemForm form) {
        item.getMedia().clear();
        if (form.mediaIds() != null) {
            item.getMedia().addAll(media.findAllById(form.mediaIds()));
        }
    }
}