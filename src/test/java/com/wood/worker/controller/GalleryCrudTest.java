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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
public class GalleryCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private JsonNode uploadPhoto() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "piece.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createCategory() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tables\",\"sortOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode createItem(Map<String, Object> fields) throws Exception {
        String item = objectMapper.writeValueAsString(fields);
        MvcResult result = mockMvc.perform(multipart("/api/admin/gallery")
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE, item.getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void adminListIncludesUnpublished() throws Exception {
        createItem(Map.of("title", "Hidden", "published", false));
        createItem(Map.of("title", "Shown", "published", true));
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/gallery")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, list.size());
    }

    @Test
    void adminListRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/gallery"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithCategoryAndMediaShowsInPublicList() throws Exception {
        JsonNode photo = uploadPhoto();
        long categoryId = createCategory();
        JsonNode created = createItem(Map.of(
                "title", "Oak Table",
                "description", "A sturdy table",
                "categoryId", categoryId,
                "sortOrder", 2,
                "published", true,
                "mediaIds", java.util.List.of(photo.get("id").asLong())));

        assertEquals("Tables", created.get("categoryName").asText());
        assertEquals(categoryId, created.get("categoryId").asLong());
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Oak Table"))
                .andExpect(jsonPath("$[0].categoryName").value("Tables"))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$[0].images[0]").value(photo.get("url").asText()));
    }

    @Test
    void createWithoutCategoryOrMediaIsAllowed() throws Exception {
        JsonNode created = createItem(Map.of(
                "title", "Solo Piece",
                "published", true));
        assertNull(created.get("categoryId").isNull() ? null : created.get("categoryId"));
        assertEquals(0, created.get("images").size());
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").isEmpty())
                .andExpect(jsonPath("$[0].images").isEmpty());
    }

    @Test
    void createIsUnpublishedByDefault() throws Exception {
        createItem(Map.of("title", "Hidden Piece"));
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void updateReplacesMediaAndCategoryAndTitle() throws Exception {
        JsonNode photoA = uploadPhoto();
        JsonNode photoB = uploadPhoto();
        long categoryA = createCategory();
        long categoryB = createCategory();
        JsonNode created = createItem(Map.of(
                "title", "Old",
                "categoryId", categoryA,
                "published", true,
                "mediaIds", java.util.List.of(photoA.get("id").asLong())));
        String form = objectMapper.writeValueAsString(Map.of(
                "title", "New",
                "categoryId", categoryB,
                "sortOrder", 9,
                "published", true,
                "mediaIds", java.util.List.of(photoB.get("id").asLong())));
        mockMvc.perform(multipart("/api/admin/gallery/" + created.get("id").asLong())
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE, form.getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.images[0]").value(photoB.get("url").asText()));
    }

    @Test
    void updateMissingItemReturnsNotFound() throws Exception {
        String form = objectMapper.writeValueAsString(Map.of("title", "X", "published", true));
        mockMvc.perform(multipart("/api/admin/gallery/9999")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE, form.getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesItemButKeepsMediaInLibrary() throws Exception {
        JsonNode photo = uploadPhoto();
        JsonNode created = createItem(Map.of(
                "title", "Temp",
                "published", true,
                "mediaIds", java.util.List.of(photo.get("id").asLong())));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/gallery/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(photo.get("id").asLong()));
    }

    @Test
    void publicGetByIdReturnsItem() throws Exception {
        JsonNode created = createItem(Map.of(
                "title", "Fetch Me",
                "published", true));
        mockMvc.perform(get("/api/gallery/" + created.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fetch Me"));
    }

    @Test
    void inlineImagePartAttachesMedia() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/gallery")
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE,
                                "{\"title\":\"Inline\",\"published\":true}".getBytes()))
                        .file(new MockMultipartFile("image", "inline.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, created.get("images").size());
        mockMvc.perform(get("/api/gallery/" + created.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0]").value(created.get("images").get(0).asText()));
    }

    @Test
    void emptyInlineImagePartIsIgnoredOnCreate() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/gallery")
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE,
                                "{\"title\":\"Noimg\",\"published\":true}".getBytes()))
                        .file(new MockMultipartFile("image", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, created.get("images").size());
    }

    @Test
    void updateWithInlineImageAttachesNewMedia() throws Exception {
        JsonNode created = createItem(Map.of("title", "Grow", "published", true));
        MvcResult result = mockMvc.perform(multipart("/api/admin/gallery/" + created.get("id").asLong())
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE,
                                "{\"title\":\"Grown2\",\"published\":true}".getBytes()))
                        .file(new MockMultipartFile("image", "up.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode updated = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, updated.get("images").size());
    }

    @Test
    void emptyInlineImagePartIsIgnoredOnUpdate() throws Exception {
        JsonNode created = createItem(Map.of("title", "Noimg2", "published", true));
        mockMvc.perform(multipart("/api/admin/gallery/" + created.get("id").asLong())
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE,
                                "{\"title\":\"Noimg3\",\"published\":true}".getBytes()))
                        .file(new MockMultipartFile("image", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isEmpty());
    }
}