package com.wood.worker.service;

import com.wood.worker.model.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaStorageService {

    private final Path uploadDir;

    public MediaStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public MediaAsset store(MultipartFile file, int sortOrder) {
        try {
            Files.createDirectories(uploadDir);
            String storedName = UUID.randomUUID() + extension(file.getOriginalFilename());
            file.transferTo(uploadDir.resolve(storedName));

            MediaAsset asset = new MediaAsset();
            asset.setStoredName(storedName);
            asset.setContentType(file.getContentType());
            asset.setSizeBytes(file.getSize());
            asset.setSortOrder(sortOrder);
            asset.setAssetType(isVideo(file) ? MediaAsset.AssetType.VIDEO : MediaAsset.AssetType.IMAGE);
            return asset;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storedName));
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