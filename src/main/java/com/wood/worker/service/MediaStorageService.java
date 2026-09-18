package com.wood.worker.service;

import com.wood.worker.model.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaStorageService {

    private final Path uploadDir;
    private final Path originalDir;
    private final ImageProcessingService imageProcessing;

    public MediaStorageService(@Value("${app.upload-dir}") String uploadDir,
                               @Value("${app.original-dir}") String originalDir,
                               ImageProcessingService imageProcessing) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.originalDir = Paths.get(originalDir).toAbsolutePath().normalize();
        this.imageProcessing = imageProcessing;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Upload directory cannot be created: " + this.uploadDir, e);
        }
        if (!Files.isWritable(this.uploadDir)) {
            throw new IllegalStateException("Upload directory is not writable: " + this.uploadDir);
        }
    }

    public MediaAsset store(MultipartFile file, int sortOrder) {
        try {
            String storedName = UUID.randomUUID() + extension(file.getOriginalFilename());
            Path display = uploadDir.resolve(storedName);
            file.transferTo(display);

            boolean isImage = !isVideo(file);
            long sizeBytes = file.getSize();
            String thumbnailName = null;
            if (isImage && imageProcessing.isSupported(display)) {
                sizeBytes = optimize(display, storedName);
                thumbnailName = generateThumbnail(storedName);
            }

            MediaAsset asset = new MediaAsset();
            asset.setStoredName(storedName);
            asset.setThumbnailName(thumbnailName);
            asset.setContentType(file.getContentType());
            asset.setSizeBytes(sizeBytes);
            asset.setSortOrder(sortOrder);
            asset.setAssetType(isImage ? MediaAsset.AssetType.IMAGE : MediaAsset.AssetType.VIDEO);
            return asset;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    /**
     * Replaces an oversized upload with a downscaled copy, preserving the full-resolution
     * original under the configured originals directory. Already-small images are left untouched.
     */
    private long optimize(Path display, String storedName) throws IOException {
        if (!imageProcessing.needsProcessing(display)) {
            return Files.size(display);
        }
        Files.createDirectories(originalDir);
        Path original = originalDir.resolve(storedName);
        Files.move(display, original, StandardCopyOption.REPLACE_EXISTING);

        Optional<ImageProcessingService.ProcessedImage> processed =
                imageProcessing.downscale(original, display);
        if (processed.isPresent()) {
            return processed.get().sizeBytes();
        }
        Files.copy(original, display, StandardCopyOption.REPLACE_EXISTING);
        return Files.size(display);
    }

    public void delete(String storedName) {
        deleteIfExists(uploadDir.resolve(storedName));
        deleteIfExists(originalDir.resolve(storedName));
    }

    public void delete(MediaAsset asset) {
        delete(asset.getStoredName());
        if (asset.getThumbnailName() != null) {
            deleteIfExists(uploadDir.resolve(asset.getThumbnailName()));
        }
    }

    /**
     * Grid-sized companion file name for a stored asset, e.g. {@code abc.jpg} →
     * {@code abc_thumb.jpg}. Lives beside the display copy so it is served the same way.
     */
    public static String thumbnailName(String storedName) {
        int dot = storedName.lastIndexOf('.');
        return dot >= 0
                ? storedName.substring(0, dot) + "_thumb" + storedName.substring(dot)
                : storedName + "_thumb";
    }

    private String generateThumbnail(String storedName) {
        String thumbName = thumbnailName(storedName);
        Optional<ImageProcessingService.ProcessedImage> processed =
                imageProcessing.thumbnail(uploadDir.resolve(storedName), uploadDir.resolve(thumbName));
        return processed.isPresent() ? thumbName : null;
    }

    public byte[] readBytes(String storedName) {
        try {
            return Files.readAllBytes(uploadDir.resolve(storedName));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file " + storedName, e);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private boolean isVideo(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            return contentType.toLowerCase(Locale.ROOT).startsWith("video/");
        }
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|mov|m4v)$");
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }
}
