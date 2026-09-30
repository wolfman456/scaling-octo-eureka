package com.wood.worker.controller;

import com.wood.worker.StubOpenAiServer;
import com.wood.worker.TestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code app.openai.timeout-ms} is honoured by the running application and not
 * merely declared. A stalled upstream used to hold the admin request thread until the
 * call itself gave up, with no bound in this project's control; now it has to surface
 * as a 502 through the normal error contract.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aitimeout;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test-password",
        "app.openai.timeout-ms=300"
})
@AutoConfigureMockMvc
@Transactional
class AdminAiTimeoutTest {

    private static final StubOpenAiServer SERVER = startStub();

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void stalledUpstreamBecomesAnErrorInsteadOfAHungRequest() throws Exception {
        SERVER.stall(3_000);

        long start = System.nanoTime();
        mockMvc.perform(multipart("/api/admin/ai/describe-image")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isBadGateway());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        org.junit.jupiter.api.Assertions.assertTrue(elapsedMillis < 2_000,
                "request took " + elapsedMillis + "ms; the timeout is not being applied");
    }
}
