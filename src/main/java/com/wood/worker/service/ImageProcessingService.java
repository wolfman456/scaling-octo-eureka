package com.wood.worker.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

/**
 * Downscales oversized photo uploads so the site never serves multi-megabyte
 * originals. JPEG only: the pixel data is re-encoded and the Exif orientation is
 * baked in, so the browser needs no metadata to display it upright.
 */
@Service
public class ImageProcessingService {

    private final int maxDimension;
    private final int thumbDimension;
    private final double quality;

    public ImageProcessingService(
            @Value("${app.image.max-dimension:2000}") int maxDimension,
            @Value("${app.image.thumb-dimension:480}") int thumbDimension,
            @Value("${app.image.quality:0.82}") double quality) {
        this.maxDimension = maxDimension;
        this.thumbDimension = thumbDimension;
        this.quality = quality;
    }

    public boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    /**
     * True when the file is a supported JPEG whose longest edge exceeds the configured
     * maximum. Missing, unreadable or unsupported files are never processed.
     */
    public boolean needsProcessing(Path file) {
        return exceeds(file, maxDimension);
    }

    private boolean exceeds(Path file, int maxEdge) {
        return readSize(file)
                .map(size -> Math.max(size.width(), size.height()) > maxEdge)
                .orElse(false);
    }

    public Optional<ImageSize> readSize(Path file) {
        if (!isSupported(file) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return Optional.of(new ImageSize(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Writes an orientation-corrected, downscaled JPEG to {@code target}. The target may
     * safely equal the source: the image is written to a sibling temp file and then moved
     * into place. Returns empty when no processing is needed or the source cannot be read.
     */
    public Optional<ProcessedImage> downscale(Path source, Path target) {
        return resize(source, target, maxDimension);
    }

    /**
     * Writes a small grid-sized JPEG to {@code target}, used by gallery/blog thumbnails.
     * Returns empty when the source is not a JPEG larger than the thumbnail edge.
     */
    public Optional<ProcessedImage> thumbnail(Path source, Path target) {
        return resize(source, target, thumbDimension);
    }

    private Optional<ProcessedImage> resize(Path source, Path target, int maxEdge) {
        if (!exceeds(source, maxEdge)) {
            return Optional.empty();
        }
        Path temp = null;
        try {
            Path parent = target.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, "img-opt-", ".jpg");
            Thumbnails.of(source.toFile())
                    .size(maxEdge, maxEdge)
                    .keepAspectRatio(true)
                    .useExifOrientation(true)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toFile(temp.toFile());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(new ProcessedImage(target, Files.size(target)));
        } catch (IOException | IllegalArgumentException e) {
            deleteQuietly(temp);
            return Optional.empty();
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }

    public record ImageSize(int width, int height) {
    }

    public record ProcessedImage(Path path, long sizeBytes) {
    }
}
