package com.wood.worker.service;

import com.wood.worker.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessingServiceTest {

    @TempDir
    Path tempDir;

    private ImageProcessingService service;

    @BeforeEach
    void setUp() {
        service = new ImageProcessingService(2000, 480, 0.82);
    }

    @Test
    void downscalesOversizedJpegWithinBounds() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("big.jpg"), 4000, 3000);
        Path target = tempDir.resolve("out.jpg");

        Optional<ImageProcessingService.ProcessedImage> result = service.downscale(source, target);

        assertTrue(result.isPresent());
        BufferedImage scaled = ImageIO.read(target.toFile());
        assertEquals(2000, Math.max(scaled.getWidth(), scaled.getHeight()));
        assertEquals(Files.size(target), result.get().sizeBytes());
    }

    @Test
    void downscalesInPlaceUsingTemporaryFile() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("inplace.jpg"), 3000, 1000);
        long before = Files.size(source);

        Optional<ImageProcessingService.ProcessedImage> result = service.downscale(source, source);

        assertTrue(result.isPresent());
        BufferedImage scaled = ImageIO.read(source.toFile());
        assertEquals(2000, scaled.getWidth());
        assertTrue(Files.size(source) < before);
    }

    @Test
    void appliesExifOrientationBeforeScaling() throws IOException {
        byte[] oriented = TestImages.jpegWithOrientation(2400, 800, 6);
        Path source = tempDir.resolve("oriented.jpg");
        Files.write(source, oriented);
        Path target = tempDir.resolve("oriented-out.jpg");

        assertTrue(service.downscale(source, target).isPresent());

        BufferedImage scaled = ImageIO.read(target.toFile());
        assertEquals(2000, scaled.getHeight());
        assertTrue(scaled.getWidth() < scaled.getHeight(), "orientation 6 should rotate landscape to portrait");
    }

    @Test
    void leavesSmallImageUntouched() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("small.jpg"), 800, 600);
        Path target = tempDir.resolve("small-out.jpg");

        assertFalse(service.needsProcessing(source));
        assertTrue(service.downscale(source, target).isEmpty());
        assertFalse(Files.exists(target));
    }

    @Test
    void thumbnailResizesToGridEdge() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("thumb-src.jpg"), 2000, 1500);
        Path target = tempDir.resolve("thumb-out.jpg");

        Optional<ImageProcessingService.ProcessedImage> result = service.thumbnail(source, target);

        assertTrue(result.isPresent());
        BufferedImage scaled = ImageIO.read(target.toFile());
        assertEquals(480, Math.max(scaled.getWidth(), scaled.getHeight()));
        assertEquals(Files.size(target), result.get().sizeBytes());
    }

    @Test
    void thumbnailSkipsSourceAlreadySmaller() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("thumb-small.jpg"), 400, 300);
        Path target = tempDir.resolve("thumb-small-out.jpg");

        assertTrue(service.thumbnail(source, target).isEmpty());
        assertFalse(Files.exists(target));
    }

    @Test
    void thumbnailIgnoresUnsupportedFormat() throws IOException {
        Path source = tempDir.resolve("thumb.png");
        Files.write(source, new byte[] {1, 2, 3, 4});

        assertTrue(service.thumbnail(source, tempDir.resolve("thumb-png-out.jpg")).isEmpty());
    }

    @Test
    void ignoresUnsupportedFormat() throws IOException {
        Path source = tempDir.resolve("photo.png");
        Files.write(source, new byte[] {1, 2, 3, 4});

        assertFalse(service.isSupported(source));
        assertFalse(service.needsProcessing(source));
        assertTrue(service.downscale(source, tempDir.resolve("png-out.jpg")).isEmpty());
    }

    @Test
    void ignoresUnreadableJpeg() throws IOException {
        Path source = tempDir.resolve("broken.jpg");
        Files.write(source, "not-an-image".getBytes(StandardCharsets.UTF_8));

        assertTrue(service.isSupported(source));
        assertFalse(service.needsProcessing(source));
        assertTrue(service.downscale(source, tempDir.resolve("broken-out.jpg")).isEmpty());
    }

    @Test
    void readSizeReturnsDimensions() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("size.jpg"), 1200, 900);

        assertEquals(new ImageProcessingService.ImageSize(1200, 900), service.readSize(source).orElseThrow());
    }

    @Test
    void acceptsAlternateJpegExtension() throws IOException {
        Path source = tempDir.resolve("photo.jpeg");
        Files.write(source, TestImages.jpeg(640, 480));

        assertTrue(service.isSupported(source));
        assertEquals(new ImageProcessingService.ImageSize(640, 480), service.readSize(source).orElseThrow());
    }

    @Test
    void readSizeIsEmptyForMissingFile() {
        assertTrue(service.readSize(tempDir.resolve("missing.jpg")).isEmpty());
    }

    @Test
    void returnsEmptyWhenTargetCannotBeWritten() throws IOException {
        Path source = TestImages.writeJpeg(tempDir.resolve("blocked.jpg"), 3000, 3000);
        Path target = Files.createDirectory(tempDir.resolve("blocked-target"));
        Files.write(target.resolve("keep.txt"), new byte[] {1});

        assertTrue(service.downscale(source, target).isEmpty());
        assertTrue(Files.exists(target.resolve("keep.txt")));
    }
}
