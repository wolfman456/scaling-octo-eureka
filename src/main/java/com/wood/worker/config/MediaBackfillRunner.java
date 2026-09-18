package com.wood.worker.config;

import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.ImageProcessingService;
import com.wood.worker.service.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * One-off maintenance pass that shrinks photos uploaded before upload-time
 * downscaling existed. Runs after the server is ready, preserves the
 * full-resolution original and is safe to run on every boot.
 */
@Component
public class MediaBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaBackfillRunner.class);

    private final MediaAssetRepository media;
    private final ImageProcessingService imageProcessing;
    private final Path uploadDir;
    private final Path originalDir;

    public MediaBackfillRunner(MediaAssetRepository media,
                               ImageProcessingService imageProcessing,
                               @Value("${app.upload-dir}") String uploadDir,
                               @Value("${app.original-dir}") String originalDir) {
        this.media = media;
        this.imageProcessing = imageProcessing;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.originalDir = Paths.get(originalDir).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            backfill();
        } catch (RuntimeException e) {
            log.warn("Media backfill failed: {}", e.getMessage(), e);
        }
    }

    /**
     * @return the number of assets optimized
     */
    public int backfill() {
        List<MediaAsset> assets = media.findByAssetType(MediaAsset.AssetType.IMAGE);
        int optimized = 0;
        for (MediaAsset asset : assets) {
            if (optimize(asset)) {
                optimized++;
            }
        }
        if (optimized > 0) {
            log.info("Optimized {} existing media asset(s)", optimized);
        }
        return optimized;
    }

    private boolean optimize(MediaAsset asset) {
        Path display = uploadDir.resolve(asset.getStoredName());
        if (!Files.isRegularFile(display)) {
            return false;
        }
        boolean changed = false;
        try {
            if (imageProcessing.needsProcessing(display)) {
                Files.createDirectories(originalDir);
                Path original = originalDir.resolve(asset.getStoredName());
                if (!Files.exists(original)) {
                    Files.copy(display, original, StandardCopyOption.COPY_ATTRIBUTES);
                }
                Optional<ImageProcessingService.ProcessedImage> processed =
                        imageProcessing.downscale(original, display);
                if (processed.isPresent()) {
                    asset.setSizeBytes(processed.get().sizeBytes());
                    changed = true;
                }
            }
            if (asset.getThumbnailName() == null && imageProcessing.isSupported(display)) {
                String thumbName = MediaStorageService.thumbnailName(asset.getStoredName());
                if (imageProcessing.thumbnail(display, uploadDir.resolve(thumbName)).isPresent()) {
                    asset.setThumbnailName(thumbName);
                    changed = true;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to optimize media asset {}: {}", asset.getId(), e.getMessage());
            return false;
        }
        if (changed) {
            media.save(asset);
        }
        return changed;
    }
}
