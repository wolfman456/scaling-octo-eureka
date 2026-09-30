package com.wood.worker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminseedreset;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=seeded-password",
        "app.admin.reset-password=reset-password"
})
@AutoConfigureMockMvc
class AdminUserSeederTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetPasswordVariableReplacesTheSeededCredential() throws Exception {
        mockMvc.perform(get("/api/admin/media").header(HttpHeaders.AUTHORIZATION, auth("admin", "reset-password")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/media").header(HttpHeaders.AUTHORIZATION, auth("admin", "seeded-password")))
                .andExpect(status().isUnauthorized());
    }

    private static String auth(String user, String password) {
        String raw = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
