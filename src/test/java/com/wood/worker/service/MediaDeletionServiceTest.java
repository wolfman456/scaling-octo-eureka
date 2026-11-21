package com.wood.worker.service;

import com.wood.worker.model.MediaAsset;
import com.wood.worker.model.PendingMediaDeletion;
import com.wood.worker.repository.PendingMediaDeletionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaDeletionServiceTest {

    @TempDir
    Path temporary;

    @Test
    void retriesAfterOnlyTheDisplayFileCanBeRemoved() throws Exception {
        var photos = temporary.resolve("display");
        var originals = Files.createDirectory(temporary.resolve("originals"));
        var storage = new MediaStorageService(photos.toString(), originals.toString(),
                mock(ImageProcessingService.class));
        var pending = mock(PendingMediaDeletionRepository.class);
        var service = new MediaDeletionService(pending, storage);

        var asset = new MediaAsset();
        asset.setStoredName("photo.jpg");
        asset.setThumbnailName("thumb.jpg");
        var entry = new PendingMediaDeletion(asset);
        when(pending.findAll()).thenReturn(List.of(entry));

        var display = Files.createFile(photos.resolve("photo.jpg"));
        var original = Files.createDirectory(originals.resolve("photo.jpg"));
        var blocker = Files.createFile(original.resolve("child"));
        var thumbnail = Files.createFile(photos.resolve("thumb.jpg"));

        service.removePendingFiles();
        assertFalse(Files.exists(display));
        assertTrue(Files.exists(thumbnail));
        verify(pending, never()).delete(entry);

        Files.delete(blocker);
        service.removePendingFiles();
        assertFalse(Files.exists(original));
        assertFalse(Files.exists(thumbnail));
        verify(pending).delete(entry);
    }
}
