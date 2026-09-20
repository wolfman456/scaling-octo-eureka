package com.wood.worker.controller;

import com.wood.worker.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:validationtest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test",
        "spring.servlet.multipart.max-file-size=10KB"
})
@AutoConfigureMockMvc
class ValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankCategoryNameReturnsFieldError() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category name is required"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void typeMismatchReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/gallery/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid value")));
    }

    @Test
    void missingItemPartReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/admin/gallery")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required part 'item' is missing"));
    }
}