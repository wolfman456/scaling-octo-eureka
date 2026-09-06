package com.wood.worker.controller;

import com.wood.worker.dto.MediaAssetDto;
import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.MediaStorageService;
import com.wood.worker.service.MediaUsageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/media")
public class AdminMediaController {

    private final MediaAssetRepository media;
    private final MediaStorageService storage;
    private final MediaUsageService usage;

    public AdminMediaController(MediaAssetRepository media, MediaStorageService storage, MediaUsageService usage) {
        this.media = media;
        this.storage = storage;
        this.usage = usage;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<MediaAssetDto> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        MediaAsset asset = storage.store(file, Math.toIntExact(media.count()));
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaAssetDto.from(media.save(asset)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<MediaAssetDto> list() {
        return media.findAllByOrderByUploadedAtDesc().stream()
                .map(MediaAssetDto::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return media.findById(id)
                .map(asset -> {
                    if (usage.isInUse(id)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).<Void>build();
                    }
                    storage.delete(asset.getStoredName());
                    media.delete(asset);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}