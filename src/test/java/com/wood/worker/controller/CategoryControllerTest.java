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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private JsonNode createCategory(String name) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", name, "sortOrder", 0));
        var result = mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void publicListIsEmptyInitially() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminListRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutSortOrderDefaultsToZero() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zero\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0));
    }

    @Test
    void createThenPublicListShowsCategory() throws Exception {
        createCategory("Dining Tables");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Dining Tables"));
    }

    @Test
    void updateChangesCategory() throws Exception {
        JsonNode created = createCategory("Tables");
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", "Coffee Tables", "sortOrder", 3));
        mockMvc.perform(put("/api/admin/categories/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Coffee Tables"))
                .andExpect(jsonPath("$.sortOrder").value(3));
    }

    @Test
    void updateMissingCategoryReturnsNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", "X", "sortOrder", 0));
        mockMvc.perform(put("/api/admin/categories/9999")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesCategory() throws Exception {
        JsonNode created = createCategory("Guards");
        mockMvc.perform(delete("/api/admin/categories/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingCategoryReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/9999")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategoryInUseReturnsConflict() throws Exception {
        JsonNode category = createCategory("Tables");
        String item = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Oak Table",
                "categoryId", category.get("id").asLong(),
                "sortOrder", 1,
                "published", true));
        mockMvc.perform(multipart("/api/admin/gallery")
                        .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE, item.getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/categories/" + category.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isConflict());
        assertEquals(1, objectMapper.readTree(mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/api/categories"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .size());
    }
}