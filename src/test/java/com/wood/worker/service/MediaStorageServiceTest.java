package com.wood.worker.service;

import com.wood.worker.TestImages;
import com.wood.worker.model.MediaAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    private MediaStorageService service;

    @BeforeEach
    void setUp() {
        service = new MediaStorageService(tempDir.toString(), originals().toString(),
                new ImageProcessingService(2000, 0.82));
    }

    private MediaAsset store(String filename, String contentType, byte[] content) {
        return service.store(new MockMultipartFile("file", filename, contentType, content), 7);
    }

    @Test
    void storesImageAndReturnsMetadata() throws IOException {
        MediaAsset asset = store("photo.PNG", "image/png", PNG());
        assertEquals(MediaAsset.AssetType.IMAGE, asset.getAssetType());
        assertTrue(asset.getStoredName().endsWith(".png"));
        assertEquals(PNG().length, asset.getSizeBytes());
        assertEquals(7, asset.getSortOrder());
        assertTrue(Files.isDirectory(tempDir));
        assertTrue(Files.exists(tempDir.resolve(asset.getStoredName())));
        assertTrue(Files.readAllBytes(tempDir.resolve(asset.getStoredName())).length > 0);
    }

@Test
    void classifiesVideoFromContentType() {
        MediaAsset asset = store("clip", "video/mp4", PNG());
        assertEquals(MediaAsset.AssetType.VIDEO, asset.getAssetType());
        assertFalse(asset.getStoredName().contains("."));
    }

    @Test
    void classifiesImageWhenContentTypeAndFilenameUnknown() {
        MediaAsset asset = service.store(
                new MockMultipartFile("file", (String) null, null, PNG()), 0);
        assertEquals(MediaAsset.AssetType.IMAGE, asset.getAssetType());
    }

    @Test
    void classifiesVideoFromFilenameWhenContentTypeUnknown() {
        MediaAsset asset = store("clip.MOV", null, PNG());
        assertEquals(MediaAsset.AssetType.VIDEO, asset.getAssetType());
    }

    @Test
    void classifiesImageWhenContentTypeIsNotVideo() {
        MediaAsset asset = store("archive.bin", "application/octet-stream", PNG());
        assertEquals(MediaAsset.AssetType.IMAGE, asset.getAssetType());
    }

    @Test
    void classifiesImageFromFilenameWhenContentTypeUnknown() {
        MediaAsset asset = store("photo.jpg", null, PNG());
        assertEquals(MediaAsset.AssetType.IMAGE, asset.getAssetType());
    }

    @Test
    void storesFileWithNoFilename() {
        MediaAsset asset = service.store(new MockMultipartFile("file", (String) null, "image/png", PNG()), 0);
        assertEquals(MediaAsset.AssetType.IMAGE, asset.getAssetType());
        assertFalse(asset.getStoredName().contains("."));
        assertTrue(Files.exists(tempDir.resolve(asset.getStoredName())));
    }

    @Test
    void storesFileWithDotlessFilename() {
        MediaAsset asset = store("noextension", "image/png", PNG());
        assertFalse(asset.getStoredName().contains("."));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        MediaAsset asset = store("photo.png", "image/png", PNG());
        Path file = tempDir.resolve(asset.getStoredName());
        assertTrue(Files.exists(file));
        service.delete(asset.getStoredName());
        assertFalse(Files.exists(file));
    }

    @Test
    void deleteMissingFileIsNoop() {
        service.delete("does-not-exist.jpg");
    }

    @Test
    void constructorFailsWhenDirectoryCannotBeCreated() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not-a-dir"));
        assertThrows(IllegalStateException.class,
                () -> new MediaStorageService(file.resolve("sub").toString(), originals().toString(), processor()));
    }

    @Test
    void constructorFailsWhenDirectoryIsNotWritable() throws IOException {
        Path readOnly = Files.createDirectories(tempDir.resolve("read-only"));
        readOnly.toFile().setWritable(false);
        try {
            assertThrows(IllegalStateException.class,
                    () -> new MediaStorageService(readOnly.toString(), originals().toString(), processor()));
        } finally {
            readOnly.toFile().setWritable(true);
        }
    }

    @Test
    void downscalesOversizedJpegAndKeepsOriginal() throws IOException {
        byte[] original = TestImages.jpeg(4000, 3000);
        MediaAsset asset = store("photo.jpg", "image/jpeg", original);

        assertTrue(asset.getSizeBytes() < original.length);
        Path display = tempDir.resolve(asset.getStoredName());
        Path savedOriginal = originals().resolve(asset.getStoredName());
        assertTrue(Files.exists(savedOriginal));
        assertEquals(original.length, Files.size(savedOriginal));

        BufferedImage scaled = ImageIO.read(display.toFile());
        assertEquals(2000, scaled.getWidth());
        assertEquals(Files.size(display), asset.getSizeBytes());
    }

    @Test
    void leavesSmallJpegWithoutOriginalCopy() throws IOException {
        byte[] small = TestImages.jpeg(800, 600);
        MediaAsset asset = store("small.jpg", "image/jpeg", small);

        assertEquals((long) small.length, asset.getSizeBytes());
        assertFalse(Files.exists(originals().resolve(asset.getStoredName())));
    }

    @Test
    void leavesVideoUntouched() {
        byte[] content = TestImages.jpeg(4000, 3000);
        MediaAsset asset = store("clip.mp4", "video/mp4", content);

        assertEquals(MediaAsset.AssetType.VIDEO, asset.getAssetType());
        assertEquals((long) content.length, asset.getSizeBytes());
    }

    @Test
    void deleteRemovesOptimizedOriginalToo() throws IOException {
        MediaAsset asset = store("delete.jpg", "image/jpeg", TestImages.jpeg(4000, 3000));
        Path display = tempDir.resolve(asset.getStoredName());
        Path original = originals().resolve(asset.getStoredName());
        assertTrue(Files.exists(display));
        assertTrue(Files.exists(original));

        service.delete(asset.getStoredName());

        assertFalse(Files.exists(display));
        assertFalse(Files.exists(original));
    }

    private Path originals() {
        return tempDir.resolve("originals");
    }

    private static ImageProcessingService processor() {
        return new ImageProcessingService(2000, 0.82);
    }

    private static byte[] PNG() {
        return new byte[]{1, 2, 3, 4};
    }
}