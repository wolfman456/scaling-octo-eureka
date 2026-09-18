package com.wood.worker.config;

import com.wood.worker.TestImages;
import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.ImageProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaBackfillRunnerTest {

    @TempDir
    Path tempDir;

    private final List<MediaAsset> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        saved.clear();
    }

    private MediaBackfillRunner runnerFor(List<MediaAsset> assets) {
        return new MediaBackfillRunner(repository(assets), processor(), tempDir.toString(),
                tempDir.resolve("originals").toString());
    }

    @Test
    void optimizesOversizedImageAndRecordsNewSize() throws Exception {
        MediaAsset asset = asset("big.jpg");
        Files.write(tempDir.resolve("big.jpg"), TestImages.jpeg(4000, 3000));

        int optimized = runnerFor(List.of(asset)).backfill();

        assertEquals(1, optimized);
        assertEquals(List.of(asset), saved);
        assertEquals(2000, ImageIO.read(tempDir.resolve("big.jpg").toFile()).getWidth());
        assertEquals(Files.size(tempDir.resolve("big.jpg")), asset.getSizeBytes());
        assertTrue(Files.exists(tempDir.resolve("originals").resolve("big.jpg")));
    }

    @Test
    void skipsAssetsAlreadyWithinBounds() throws Exception {
        MediaAsset asset = asset("small.jpg");
        Files.write(tempDir.resolve("small.jpg"), TestImages.jpeg(800, 600));

        assertEquals(0, runnerFor(List.of(asset)).backfill());
        assertTrue(saved.isEmpty());
    }

    @Test
    void skipsMissingFile() {
        assertEquals(0, runnerFor(List.of(asset("gone.jpg"))).backfill());
        assertTrue(saved.isEmpty());
    }

    @Test
    void isIdempotent() throws Exception {
        MediaAsset asset = asset("repeat.jpg");
        Files.write(tempDir.resolve("repeat.jpg"), TestImages.jpeg(4000, 3000));
        MediaBackfillRunner runner = runnerFor(List.of(asset));

        assertEquals(1, runner.backfill());
        assertEquals(0, runner.backfill());
        assertEquals(List.of(asset), saved);
    }

    @Test
    void keepsExistingOriginalCopy() throws Exception {
        MediaAsset asset = asset("kept.jpg");
        Files.write(tempDir.resolve("kept.jpg"), TestImages.jpeg(4000, 3000));
        Path originals = Files.createDirectories(tempDir.resolve("originals"));
        Files.write(originals.resolve("kept.jpg"), TestImages.jpeg(5000, 2000));

        assertEquals(1, runnerFor(List.of(asset)).backfill());

        assertEquals(5000, ImageIO.read(originals.resolve("kept.jpg").toFile()).getWidth());
        assertEquals(2000, ImageIO.read(tempDir.resolve("kept.jpg").toFile()).getWidth());
    }

    @Test
    void skipsAssetWhenOriginalsDirectoryBlocks() throws Exception {
        MediaAsset asset = asset("locked.jpg");
        Files.write(tempDir.resolve("locked.jpg"), TestImages.jpeg(4000, 3000));
        Files.createFile(tempDir.resolve("originals"));

        assertEquals(0, runnerFor(List.of(asset)).backfill());
        assertTrue(saved.isEmpty());
    }

    @Test
    void applicationReadySwallowsRepositoryFailures() {
        MediaAssetRepository failing = (MediaAssetRepository) Proxy.newProxyInstance(
                MediaAssetRepository.class.getClassLoader(),
                new Class<?>[] {MediaAssetRepository.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("boom");
                });
        MediaBackfillRunner runner = new MediaBackfillRunner(failing, processor(), tempDir.toString(),
                tempDir.resolve("originals").toString());

        assertDoesNotThrow(runner::onApplicationReady);
    }

    private static MediaAsset asset(String storedName) {
        MediaAsset asset = new MediaAsset();
        asset.setStoredName(storedName);
        asset.setAssetType(MediaAsset.AssetType.IMAGE);
        asset.setSizeBytes(9_000_000L);
        asset.setSortOrder(0);
        return asset;
    }

    private static ImageProcessingService processor() {
        return new ImageProcessingService(2000, 0.82);
    }

    private MediaAssetRepository repository(List<MediaAsset> assets) {
        return (MediaAssetRepository) Proxy.newProxyInstance(
                MediaAssetRepository.class.getClassLoader(),
                new Class<?>[] {MediaAssetRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByAssetType" -> assets;
                    case "save" -> {
                        saved.add((MediaAsset) args[0]);
                        yield args[0];
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
