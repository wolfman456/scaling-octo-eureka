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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private JsonNode putSettings(String body) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getSettings(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void defaultsReturned() throws Exception {
        JsonNode settings = getSettings("/api/settings");
        assertEquals("Six Kids Crafts", settings.get("siteTitle").asText());
        assertNull(settings.get("backgroundMediaId").asText().isEmpty() ? null : settings.get("backgroundMediaId").asText());
        assertNull(settings.get("backgroundImage").isNull() ? null : settings.get("backgroundImage"));
        assertNull(settings.get("contactEmail").isNull() ? null : settings.get("contactEmail"));
    }

    @Test
    void updateAppliesProvidedValues() throws Exception {
        putSettings("{\"siteTitle\":\"Wolf Works\",\"contactEmail\":\"hi@wolf.works\",\"instagramUrl\":\"http://ig\"}");
        JsonNode settings = getSettings("/api/settings");
        assertEquals("Wolf Works", settings.get("siteTitle").asText());
        assertEquals("hi@wolf.works", settings.get("contactEmail").asText());
        assertEquals("http://ig", settings.get("instagramUrl").asText());
    }

    @Test
    void updateLeavesUnprovidedValuesIntact() throws Exception {
        putSettings("{\"siteTitle\":\"Wolf Works\",\"contactEmail\":\"hi@wolf.works\"}");
        putSettings("{\"siteTitle\":\"Wolf Works Two\"}");
        JsonNode settings = getSettings("/api/settings");
        assertEquals("Wolf Works Two", settings.get("siteTitle").asText());
        assertEquals("hi@wolf.works", settings.get("contactEmail").asText());
    }

    @Test
    void emptyStringClearsSetting() throws Exception {
        putSettings("{\"contactEmail\":\"hi@wolf.works\"}");
        putSettings("{\"contactEmail\":\"\"}");
        JsonNode settings = getSettings("/api/settings");
        assertNull(settings.get("contactEmail").isNull() ? null : settings.get("contactEmail"));
    }

    @Test
    void emptyStringForUnsetSettingIsHarmless() throws Exception {
        putSettings("{\"instagramUrl\":\"\"}");
        JsonNode settings = getSettings("/api/settings");
        assertNull(settings.get("instagramUrl").isNull() ? null : settings.get("instagramUrl"));
    }

    @Test
    void backgroundReferenceResolvesToUrl() throws Exception {
        MvcResult upload = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "bg.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
        long mediaId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();

        JsonNode updated = putSettings("{\"backgroundMediaId\":" + mediaId + "}");
        String url = updated.get("backgroundImage").asText();
        assertEquals(mediaId, updated.get("backgroundMediaId").asLong());
        org.junit.jupiter.api.Assertions.assertTrue(url.startsWith("/uploads/"));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundImage").value(url));
    }

    @Test
    void publicSettingsMatchAdminSettings() throws Exception {
        putSettings("{\"siteTitle\":\"Wolf Works\",\"contactEmail\":\"hi@wolf.works\",\"facebookUrl\":\"http://fb\"}");
        MvcResult adminResult = mockMvc.perform(get("/api/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode admin = objectMapper.readTree(adminResult.getResponse().getContentAsString());
        JsonNode pub = getSettings("/api/settings");
        assertEquals(admin.get("siteTitle").asText(), pub.get("siteTitle").asText());
        assertEquals(admin.get("contactEmail").asText(), pub.get("contactEmail").asText());
        assertEquals(admin.get("facebookUrl").asText(), pub.get("facebookUrl").asText());
    }

    @Test
    void updateRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteTitle\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }
}