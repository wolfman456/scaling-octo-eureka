package com.wood.worker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PendingMediaDeletion {

    @Id
    private String storedName;

    private String thumbnailName;

    protected PendingMediaDeletion() {
    }

    public PendingMediaDeletion(MediaAsset asset) {
        this.storedName = asset.getStoredName();
        this.thumbnailName = asset.getThumbnailName();
    }

    public String getStoredName() {
        return storedName;
    }

    public String getThumbnailName() {
        return thumbnailName;
    }
}
