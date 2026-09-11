package com.wood.worker.controller;

import com.wood.worker.StubOpenAiServer;
import com.wood.worker.TestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gallerytest-ai;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test"
})
@AutoConfigureMockMvc
@Transactional
class AdminAiControllerTest {

    private static final StubOpenAiServer SERVER = startStub();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static StubOpenAiServer startStub() {
        try {
            StubOpenAiServer server = new StubOpenAiServer();
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopStub() {
        SERVER.stop();
    }

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.openai.api-key", () -> "sk-fake");
        registry.add("app.openai.base-url", SERVER::baseUrl);
    }

    private String contentResponse(String content) {
        tools.jackson.databind.node.ObjectNode message = objectMapper.createObjectNode()
                .put("role", "assistant")
                .put("content", content);
        tools.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        root.set("choices", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().set("message", message)));
        return objectMapper.writeValueAsString(root);
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "photo.png", "image/png", TestSupport.PNG_BYTES);
    }

    private String uploadPhoto() throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/media")
                        .file(png())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void describeImageRequiresAuth() throws Exception {
        mockMvc.perform(multipart("/api/admin/ai/describe-image").file(png()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void draftArticleRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/admin/ai/draft-article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void describeImageRejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void describeImageRejectsNonImage() throws Exception {
        mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .file(new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void describeImageRejectsEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void describeImageReturnsDescription() throws Exception {
        SERVER.respond(contentResponse("A hand-built oak table."));
        String body = mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .file(png())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("A hand-built oak table.", objectMapper.readTree(body).get("description").asText());
    }

    @Test
    void draftArticleReturnsDraft() throws Exception {
        SERVER.respond(contentResponse("{\"title\":\"New Post\",\"bodyMd\":\"## Body\"}"));
        String body = mockMvc.perform(post("/api/admin/ai/draft-article")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"side table\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        tools.jackson.databind.JsonNode json = objectMapper.readTree(body);
        assertEquals("New Post", json.get("title").asText());
        assertEquals("## Body", json.get("bodyMd").asText());
    }

    @Test
    void draftArticleWithNullTopicStillDrafts() throws Exception {
        SERVER.respond(contentResponse("{\"title\":\"T\",\"bodyMd\":\"B\"}"));
        mockMvc.perform(post("/api/admin/ai/draft-article")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":null}"))
                .andExpect(status().isOk());
        assertTrue(SERVER.lastRequestBody().contains("\"messages\""));
    }

    @Test
    void draftArticleWithPhotosSendsImagesToOpenAi() throws Exception {
        String mediaId = uploadPhoto();
        SERVER.respond(contentResponse("{\"title\":\"T\",\"bodyMd\":\"B\"}"));
        mockMvc.perform(post("/api/admin/ai/draft-article")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"table\",\"mediaIds\":[" + mediaId + "]}"))
                .andExpect(status().isOk());
        assertTrue(SERVER.lastRequestBody().contains("data:image/png;base64"));
    }

    @Test
    void propagatesOpenAiErrorAsBadGateway() throws Exception {
        SERVER.respond(500, "{\"error\":\"boom\"}");
        mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .file(png())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadGateway());
    }
}