package com.wood.worker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gallerytest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test"
})
@AutoConfigureMockMvc
class SpaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootServesSpaShell() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void deepLinkServesSpaShell() throws Exception {
        mockMvc.perform(get("/articles/some-post"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void adminRouteServesSpaShell() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void apiRoutesTakePrecedenceOverSpaFallback() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl((String) null));
    }
}