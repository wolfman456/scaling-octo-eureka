package com.wood.worker.service;

import com.wood.worker.model.MediaAsset;
import com.wood.worker.model.PendingMediaDeletion;
import com.wood.worker.repository.PendingMediaDeletionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MediaDeletionService {

    private static final Logger log = LoggerFactory.getLogger(MediaDeletionService.class);

    private final PendingMediaDeletionRepository pending;
    private final MediaStorageService storage;

    public MediaDeletionService(PendingMediaDeletionRepository pending, MediaStorageService storage) {
        this.pending = pending;
        this.storage = storage;
    }

    public void enqueue(MediaAsset asset) {
        pending.save(new PendingMediaDeletion(asset));
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void removePendingFiles() {
        for (PendingMediaDeletion deletion : pending.findAll()) {
            try {
                storage.deleteFiles(deletion.getStoredName(), deletion.getThumbnailName());
                pending.delete(deletion);
            } catch (RuntimeException e) {
                log.warn("Will retry file cleanup for '{}'", deletion.getStoredName(), e);
            }
        }
    }
}
