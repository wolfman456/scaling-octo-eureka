package com.wood.worker.service;

import com.wood.worker.model.MediaAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    private MediaStorageService service;

    @BeforeEach
    void setUp() {
        service = new MediaStorageService(tempDir.toString());
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

    private static byte[] PNG() {
        return new byte[]{1, 2, 3, 4};
    }
}