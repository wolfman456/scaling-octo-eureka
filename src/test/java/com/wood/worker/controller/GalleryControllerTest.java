package com.wood.worker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GalleryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsEmptyWhenNothingPublished() throws Exception {
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void unknownItemReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/gallery/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/gallery"))
                .andExpect(status().isUnauthorized());
    }
}