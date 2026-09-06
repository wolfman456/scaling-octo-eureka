package com.wood.worker.controller;

import tools.jackson.databind.JsonNode;
import com.wood.worker.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gallerytest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test"
})
@AutoConfigureMockMvc
@Transactional
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "photo.png", "image/png", TestSupport.PNG_BYTES);
    }

    private JsonNode uploadPhoto() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(png())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/admin/media").file(png()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/media"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadEmptyFileReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadStoresFileAndServesIt() throws Exception {
        JsonNode json = uploadPhoto();
        assertEquals("IMAGE", json.get("assetType").asText());
        assertTrue(json.get("url").asText().startsWith("/uploads/"));
        assertEquals("/uploads/" + Path.of(json.get("url").asText()).getFileName(), json.get("url").asText());
        assertTrue(Files.exists(Path.of("target/test-uploads", Path.of(json.get("url").asText()).getFileName().toString())));

        mockMvc.perform(get(json.get("url").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void listReturnsUploadedAssets() throws Exception {
        JsonNode uploaded = uploadPhoto();
        MvcResult result = mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, list.size());
        assertEquals(uploaded.get("id").asLong(), list.get(0).get("id").asLong());
    }

    @Test
    void deleteUnusedAssetRemovesItAndItsFile() throws Exception {
        JsonNode uploaded = uploadPhoto();
        String storedName = Path.of(uploaded.get("url").asText()).getFileName().toString();
        assertTrue(Files.exists(Path.of("target/test-uploads", storedName)));

        mockMvc.perform(delete("/api/admin/media/" + uploaded.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNoContent());

        assertFalse(Files.exists(Path.of("target/test-uploads", storedName)));
    }

    @Test
    void deleteMissingAssetReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/media/9999")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAssetInUseByGalleryItemReturnsConflict() throws Exception {
        JsonNode media = uploadPhoto();
        attachToGalleryItem(String.valueOf(media.get("id").asLong()));
        mockMvc.perform(delete("/api/admin/media/" + media.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteAssetInUseByArticleReturnsConflict() throws Exception {
        JsonNode media = uploadPhoto();
        String item = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Post",
                "published", true,
                "mediaIds", java.util.List.of(media.get("id").asLong())));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/articles")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(item))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/media/" + media.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteAssetInUseAsFeaturedImageReturnsConflict() throws Exception {
        JsonNode media = uploadPhoto();
        String article = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Featured",
                "published", true,
                "featuredMediaId", media.get("id").asLong()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/articles")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(article))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/media/" + media.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteAssetInUseAsBackgroundReturnsConflict() throws Exception {
        JsonNode media = uploadPhoto();
        String settings = objectMapper.writeValueAsString(java.util.Map.of("backgroundMediaId", media.get("id").asLong()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settings))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/media/" + media.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isConflict());
    }

    private void attachToGalleryItem(String mediaId) throws Exception {
        String item = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Table",
                "published", true,
                "mediaIds", java.util.List.of(mediaId)));
        mockMvc.perform(multipart("/api/admin/gallery")
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE, item.getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated());
    }
}