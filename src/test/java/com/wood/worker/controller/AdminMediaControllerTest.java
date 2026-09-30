package com.wood.worker.controller;

import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.MediaStorageService;
import com.wood.worker.service.MediaUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

class AdminMediaControllerTest {

    private static final MultipartFile UPLOAD =
            new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

    private MediaAssetRepository media;
    private MediaStorageService storage;
    private MediaUsageService usage;
    private AdminMediaController controller;

    @BeforeEach
    void setUp() {
        media = Mockito.mock(MediaAssetRepository.class);
        storage = Mockito.mock(MediaStorageService.class);
        usage = Mockito.mock(MediaUsageService.class);
        controller = new AdminMediaController(media, storage, usage);
    }

    @Test
    void uploadArmsRollbackCleanupBeforeSavingTheRow() {
        MediaAsset asset = asset();
        Mockito.when(storage.store(any())).thenReturn(asset);
        Mockito.when(media.save(any())).thenReturn(asset);

        controller.upload(UPLOAD);

        // Armed first, so a failure *after* the save still removes the file.
        var order = Mockito.inOrder(storage, media);
        order.verify(storage).store(any());
        order.verify(storage).deleteOnRollback(asset);
        order.verify(media).save(asset);
    }

    @Test
    void uploadLeavesCleanupToRollbackWhenTheRowCannotBeSaved() {
        Mockito.when(storage.store(any())).thenReturn(asset());
        Mockito.when(media.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThrows(DataIntegrityViolationException.class, () -> controller.upload(UPLOAD));

        // The rollback callback owns cleanup; doing it here too would race it.
        Mockito.verify(storage).deleteOnRollback(any());
        Mockito.verify(storage, Mockito.never()).deleteAfterFailedSave(any());
    }

    @Test
    void uploadRejectsAnEmptyFile() {
        var response = controller.upload(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]));

        assertEquals(400, response.getStatusCode().value());
        Mockito.verify(storage, Mockito.never()).store(any());
    }

    @Test
    void deleteKeepsTheRowWhenTheFileCannotBeRemoved() {
        MediaAsset asset = asset();
        Mockito.when(media.findById(1L)).thenReturn(Optional.of(asset));
        Mockito.doThrow(new IllegalStateException("Failed to delete stored file"))
                .when(storage).delete(asset);

        // The real transaction rolls the delete back here, restoring the row with
        // its files still on disk.
        assertThrows(IllegalStateException.class, () -> controller.delete(1L));
    }

    @Test
    void deleteRemovesTheRowAndFlushesBeforeTouchingFiles() {
        MediaAsset asset = asset();
        Mockito.when(media.findById(1L)).thenReturn(Optional.of(asset));

        var response = controller.delete(1L);

        assertEquals(204, response.getStatusCode().value());
        var order = Mockito.inOrder(media, storage);
        order.verify(media).delete(asset);
        order.verify(media).flush();
        // Only once the foreign key has been checked is it safe to unlink the file.
        order.verify(storage).delete(asset);
    }

    @Test
    void deleteRefusesAnAssetThatIsInUse() {
        MediaAsset asset = asset();
        Mockito.when(media.findById(1L)).thenReturn(Optional.of(asset));
        Mockito.when(usage.isInUse(1L)).thenReturn(true);

        var response = controller.delete(1L);

        assertEquals(409, response.getStatusCode().value());
        Mockito.verify(storage, Mockito.never()).delete(any(MediaAsset.class));
        Mockito.verify(media, Mockito.never()).delete(any(MediaAsset.class));
    }

    @Test
    void deleteMissingAssetReturnsNotFound() {
        Mockito.when(media.findById(9L)).thenReturn(Optional.empty());

        assertEquals(404, controller.delete(9L).getStatusCode().value());
    }

    private static MediaAsset asset() {
        MediaAsset asset = new MediaAsset();
        asset.setStoredName("stored.png");
        asset.setAssetType(MediaAsset.AssetType.IMAGE);
        return asset;
    }
}
