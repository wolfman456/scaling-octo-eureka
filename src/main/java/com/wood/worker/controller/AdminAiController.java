package com.wood.worker.controller;

import com.wood.worker.dto.ArticleDraftDto;
import com.wood.worker.dto.ArticleDraftRequest;
import com.wood.worker.dto.ImageDescriptionDto;
import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.AiCallException;
import com.wood.worker.service.AiDisabledException;
import com.wood.worker.service.OpenAiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {

    private final OpenAiService ai;
    private final MediaAssetRepository media;

    public AdminAiController(OpenAiService ai, MediaAssetRepository media) {
        this.ai = ai;
        this.media = media;
    }

    @PostMapping("/describe-image")
    public ResponseEntity<ImageDescriptionDto> describeImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !isImage(file)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new ImageDescriptionDto(ai.describeImage(file)));
    }

    @PostMapping("/draft-article")
    @Transactional(readOnly = true)
    public ResponseEntity<ArticleDraftDto> draftArticle(@RequestBody ArticleDraftRequest request) {
        List<MediaAsset> photos = request.mediaIds() == null
                ? List.of()
                : media.findAllById(request.mediaIds());
        return ResponseEntity.ok(ai.draftArticle(request.topic(), photos));
    }

    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<String> handleDisabled(AiDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(AiCallException.class)
    public ResponseEntity<String> handleCall(AiCallException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }

    private boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }
}