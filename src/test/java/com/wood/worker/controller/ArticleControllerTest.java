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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private JsonNode createArticle(String bodyJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/articles")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void adminCreateRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createGeneratesSlugFromTitle() throws Exception {
        JsonNode created = createArticle("{\"title\":\"Building a Farm Table\"}");
        assertEquals("building-a-farm-table", created.get("slug").asText());
    }

    @Test
    void createWithDuplicateTitleAppendsSuffix() throws Exception {
        createArticle("{\"title\":\"Farm Table\"}");
        JsonNode second = createArticle("{\"title\":\"Farm Table\"}");
        assertEquals("farm-table-2", second.get("slug").asText());
    }

    @Test
    void createWithExplicitSlugUsesIt() throws Exception {
        JsonNode created = createArticle("{\"title\":\"Whatever\",\"slug\":\"My Custom Slug\"}");
        assertEquals("my-custom-slug", created.get("slug").asText());
    }

    @Test
    void createWithBlankSlugFallsBackToTitle() throws Exception {
        JsonNode created = createArticle("{\"title\":\"Fancy Sign\",\"slug\":\"\"}");
        assertEquals("fancy-sign", created.get("slug").asText());
    }

    @Test
    void unpublishedArticleHiddenFromPublic() throws Exception {
        createArticle("{\"title\":\"Draft\",\"published\":false}");
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/articles/draft"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishedArticleAppearsInPublicList() throws Exception {
        JsonNode article = createArticle("{\"title\":\"Assembly Tips\",\"published\":true}");
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value(article.get("slug").asText()))
                .andExpect(jsonPath("$[0].title").value("Assembly Tips"));
    }

    @Test
    void publicListResolvesFeaturedImage() throws Exception {
        JsonNode featured = uploadPhoto("summary-featured.png");
        String featuredUrl = featured.get("url").asText();
        createArticle("{\"title\":\"With Featured\",\"published\":true,"
                + "\"featuredMediaId\":" + featured.get("id").asLong() + ","
                + "\"mediaIds\":[" + featured.get("id").asLong() + "]}");
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].featuredImage").value(featuredUrl))
                .andExpect(jsonPath("$[0].images[0]").value(featuredUrl));
    }

    @Test
    void getPublishedArticleBySlug() throws Exception {
        JsonNode article = createArticle("{\"title\":\"Choices of Wood\",\"published\":true}");
        mockMvc.perform(get("/api/articles/" + article.get("slug").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Choices of Wood"));
    }

    @Test
    void getUnknownSlugReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/articles/no-such-post"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithMediaAttachesImagesAndFeatured() throws Exception {
        JsonNode image = uploadPhoto("photo-a.png");
        JsonNode featured = uploadPhoto("featured.png");
        String featuredUrl = featured.get("url").asText();
        String imageUrl = image.get("url").asText();
        String body = "{\"title\":\"Shop Tour\",\"published\":true,"
                + "\"featuredMediaId\":" + featured.get("id").asLong() + ","
                + "\"mediaIds\":[" + image.get("id").asLong() + "," + featured.get("id").asLong() + "]}";
        JsonNode created = createArticle(body);
        assertEquals(featuredUrl, created.get("featuredImage").asText());
        assertEquals(2, created.get("images").size());

        mockMvc.perform(get("/api/articles/" + created.get("slug").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featuredImage").value(featuredUrl));
    }

    @Test
    void updateChangesFieldsAndKeepsSlugWhenUnchanged() throws Exception {
        JsonNode created = createArticle("{\"title\":\"Original\",\"published\":true}");
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Changed", "slug", created.get("slug").asText(), "published", false));
        mockMvc.perform(put("/api/admin/articles/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Changed"))
                .andExpect(jsonPath("$.slug").value(created.get("slug").asText()));

        mockMvc.perform(get("/api/articles/" + created.get("slug").asText()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithNewSlugReSlugsArticle() throws Exception {
        JsonNode created = createArticle("{\"title\":\"Original Post\"}");
        String body = objectMapper.writeValueAsString(java.util.Map.of("title", "Original Post", "slug", "brand-new"));
        mockMvc.perform(put("/api/admin/articles/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("brand-new"));
    }

    @Test
    void republishingKeepsExistingPublishedAt() throws Exception {
        JsonNode created = createArticle("{\"title\":\"Repub\",\"published\":false}");
        String publish1 = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Repub", "published", true));
        String publish2 = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Repub", "published", true));
        MvcResult first = mockMvc.perform(put("/api/admin/articles/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publish1))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String publishedAt1 = firstJson.get("publishedAt").asText();

        MvcResult second = mockMvc.perform(put("/api/admin/articles/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publish2))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        assertEquals(publishedAt1, secondJson.get("publishedAt").asText());
    }

    @Test
    void updateMissingArticleReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/admin/articles/9999")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesArticle() throws Exception {
        JsonNode created = createArticle("{\"title\":\"To Delete\"}");
        mockMvc.perform(delete("/api/admin/articles/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteMissingArticleReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/articles/9999")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminListIncludesUnpublishedAndPrivateFields() throws Exception {
        createArticle("{\"title\":\"Visible\",\"published\":true}");
        createArticle("{\"title\":\"Hidden\",\"published\":false}");
        MvcResult result = mockMvc.perform(get("/api/admin/articles")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, list.size());
    }

    private JsonNode uploadPhoto(String filename) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", filename, "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}