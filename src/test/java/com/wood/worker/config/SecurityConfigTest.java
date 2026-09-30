package com.wood.worker.config;

import com.wood.worker.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securityconfigtest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=" + TestSupport.ADMIN_USER,
        "app.admin.password=" + TestSupport.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void h2ConsoleStaysReachableWithoutCredentialsOutsideProduction() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isOk());
    }

    @Test
    void consoleChainIsRestrictedToNonProductionProfiles() throws Exception {
        Profile profile = SecurityConfig.class
                .getDeclaredMethod("h2ConsoleSecurityFilterChain", HttpSecurity.class)
                .getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!production");
    }

    @Test
    void adminApiStillRequiresCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/media"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiAcceptsTheSeededAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isOk());
    }

    @Test
    void publicApiStaysOpen() throws Exception {
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk());
    }
}
