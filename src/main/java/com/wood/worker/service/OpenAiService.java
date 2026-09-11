package com.wood.worker.service;

import com.wood.worker.dto.ArticleDraftDto;
import com.wood.worker.model.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
public class OpenAiService {

    private static final int MAX_ARTICLE_PHOTOS = 4;
    private static final int MAX_DESCRIPTION_TOKENS = 200;
    private static final int MAX_ARTICLE_TOKENS = 1200;

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final MediaStorageService storage;

    public OpenAiService(@Value("${app.openai.api-key:}") String apiKey,
                         @Value("${app.openai.base-url}") String baseUrl,
                         @Value("${app.openai.model}") String model,
                         RestClient.Builder builder,
                         ObjectMapper mapper,
                         MediaStorageService storage) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.mapper = mapper;
        this.storage = storage;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .build();
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    public String describeImage(MultipartFile image) {
        requireEnabled();
        String prompt = "Write a short, natural alt-text description (under 80 words) of this handmade "
                + "wooden piece, e.g. 'A hand-built oak coffee table with a natural shelf and clear-coated "
                + "top.' Say what it is and, where visible, its material, finish, and character. Do not "
                + "invent brand names, prices, owners, or usage details that are not visible. Return only "
                + "the description.";
        ArrayNode parts = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("type", "text").put("text", prompt))
                .add(imagePart(image.getContentType(), imageBytes(image)));
        String content = complete(parts, MAX_DESCRIPTION_TOKENS, null);
        if (content.length() > 500) {
            content = content.substring(0, 497).trim() + "...";
        }
        return content;
    }

    public ArticleDraftDto draftArticle(String topic, List<MediaAsset> photos) {
        requireEnabled();
        String text = "You write portfolio posts for a small woodworking business. Draft a post"
                + (topic == null || topic.isBlank() ? "" : " about: " + topic.trim())
                + ". Describe only what a reader can see or what is given above; never invent materials, "
                + "prices, dimensions, or client stories. Reply with JSON only, shaped exactly as "
                + "{\"title\": \"...\", \"bodyMd\": \"...\"} where bodyMd is Markdown of 400-600 words and "
                + "title is a short blog-post title.";
        ArrayNode parts = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("type", "text").put("text", text));
        int photosAdded = 0;
        for (MediaAsset photo : photos == null ? List.<MediaAsset>of() : photos) {
            if (photosAdded >= MAX_ARTICLE_PHOTOS || photo == null || photo.getStoredName() == null) {
                continue;
            }
            String contentType = photo.getContentType() == null ? "image/jpeg" : photo.getContentType();
            parts.add(imagePart(contentType, readPhotoBytes(photo.getStoredName())));
            photosAdded++;
        }
        String json = complete(parts, MAX_ARTICLE_TOKENS, "json_object");
        try {
            JsonNode draft = mapper.readTree(json);
            String title = draft.path("title").asText(null);
            String bodyMd = draft.path("bodyMd").asText(null);
            if (title == null || title.isBlank() || bodyMd == null || bodyMd.isBlank()) {
                throw new AiCallException("OpenAI draft was missing title or bodyMd");
            }
            return new ArticleDraftDto(title.trim(), bodyMd.trim());
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw new AiCallException("Could not parse OpenAI draft", e);
        }
    }

    private String complete(ArrayNode parts, int maxTokens, String responseFormat) {
        ObjectNode body = mapper.createObjectNode()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("n", 1);
        body.set("messages", mapper.createArrayNode()
                .add(systemMessage())
                .add(mapper.createObjectNode().put("role", "user").set("content", parts)));
        if (responseFormat != null) {
            body.set("response_format", mapper.createObjectNode().put("type", responseFormat));
        }
        return complete(body);
    }

    private String complete(ObjectNode body) {
        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsBytes(body))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        throw new AiCallException(
                                "OpenAI request failed with HTTP " + httpResponse.getStatusCode().value());
                    })
                    .body(String.class);
            JsonNode root = mapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new AiCallException("OpenAI returned no message content");
            }
            return content.trim();
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw new AiCallException("Failed to reach OpenAI: " + e.getMessage(), e);
        }
    }

    private ObjectNode systemMessage() {
        return mapper.createObjectNode().put("role", "system")
                .put("content", "You produce helpful, honest copy for a woodworking portfolio site. "
                        + "Never fabricate details; write plain, warm English.");
    }

    private ObjectNode imagePart(String contentType, byte[] bytes) {
        String dataUrl = "data:" + (contentType == null ? "image/jpeg" : contentType)
                + ";base64," + Base64.getEncoder().encodeToString(bytes);
        ObjectNode image = mapper.createObjectNode().put("type", "image_url");
        image.set("image_url", mapper.createObjectNode().put("url", dataUrl));
        return image;
    }

    private byte[] readPhotoBytes(String storedName) {
        try {
            return storage.readBytes(storedName);
        } catch (IllegalStateException e) {
            throw new AiCallException("Could not read stored photo for drafting", e);
        }
    }

    private byte[] imageBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new AiCallException("Failed to read uploaded image", e);
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new AiDisabledException(
                    "AI features are disabled - set OPENAI_API_KEY to enable them.");
        }
    }
}