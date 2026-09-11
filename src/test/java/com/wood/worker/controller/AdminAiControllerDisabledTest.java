package com.wood.worker.controller;

import com.wood.worker.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gallerytest-ai-disabled;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test",
        "app.openai.api-key="
})
@AutoConfigureMockMvc
@Transactional
class AdminAiControllerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void describeImageDisabledReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$", containsString("AI features are disabled")));
    }

    @Test
    void draftArticleDisabledReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(post("/api/admin/ai/draft-article")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"table\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$", containsString("AI features are disabled")));
    }
}