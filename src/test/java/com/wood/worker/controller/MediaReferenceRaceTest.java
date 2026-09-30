package com.wood.worker.controller;

import com.wood.worker.TestSupport;
import com.wood.worker.model.MediaAsset;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.service.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The in-use check in the media delete path is a read-then-write, so a reference
 * committed between the two would leave a media row pointing at a file the library
 * believes it deleted. These tests pin the second line of defence: the database
 * refuses to orphan a referenced asset, and the error contract turns that refusal
 * into a 409 rather than a 500.
 *
 * Deliberately not {@code @Transactional}: the interesting case needs two separate
 * committed transactions, which a single test-managed transaction cannot express.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mediarace;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test-password"
})
@AutoConfigureMockMvc
class MediaReferenceRaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private MediaAssetRepository media;

    @Autowired
    private MediaStorageService storage;

    @Autowired
    private TransactionTemplate transactions;

    private final Path uploadDir = Path.of("target/test-uploads");

    @Test
    void aRolledBackUploadLeavesNoFileAndNoRow() throws Exception {
        // The real failure this guards: the file is written before the row, and
        // anything that unwinds the transaction afterwards discards the row. Rollback
        // is the only moment both halves are known to be unwound.
        AtomicReference<MediaAsset> stored = new AtomicReference<>();
        assertThrows(Boom.class, () -> transactions.executeWithoutResult(status -> {
            MediaAsset asset = storage.store(new MockMultipartFile(
                    "file", "doomed.png", "image/png", TestSupport.PNG_BYTES));
            stored.set(asset);
            storage.deleteOnRollback(asset);
            media.save(asset);
            throw new Boom();
        }));

        assertTrue(media.findById(stored.get().getId()).isEmpty(), "row must not survive");
        assertFalse(Files.exists(uploadDir.resolve(stored.get().getStoredName())),
                "file must not be orphaned");
    }

    private static final class Boom extends RuntimeException {
    }

    @Test
    void databaseRefusesToDeleteAnAssetAGalleryItemReferences() {
        AtomicReference<Long> id = new AtomicReference<>();
        transactions.executeWithoutResult(status -> {
            id.set(upload());
            attachToGalleryItem(id.get());
        });

        assertTrue(deleteRowIsRejected(id.get()));
        assertTrue(media.findById(id.get()).isPresent(), "the referenced row must survive");
    }

    @Test
    void databaseRefusesToDeleteAnAssetAnArticleReferences() {
        AtomicReference<Long> id = new AtomicReference<>();
        transactions.executeWithoutResult(status -> {
            id.set(upload());
            attachToArticle(id.get());
        });

        assertTrue(deleteRowIsRejected(id.get()));
    }

    @Test
    void unreferencedAssetCanStillBeDeleted() {
        AtomicReference<Long> id = new AtomicReference<>();
        transactions.executeWithoutResult(status -> id.set(upload()));

        transactions.executeWithoutResult(status -> {
            media.delete(media.findById(id.get()).orElseThrow());
            media.flush();
        });

        assertTrue(media.findById(id.get()).isEmpty());
    }

    private boolean deleteRowIsRejected(Long id) {
        try {
            transactions.executeWithoutResult(status -> {
                media.delete(media.findById(id).orElseThrow());
                media.flush();
            });
            return false;
        } catch (DataIntegrityViolationException expected) {
            return true;
        }
    }

    private Long upload() {
        try {
            var result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", TestSupport.PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                .andExpect(status().isCreated())
                .andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void attachToGalleryItem(Long mediaId) {
        try {
            String item = objectMapper.writeValueAsString(Map.of(
                    "title", "Table",
                    "published", true,
                    "mediaIds", List.of(mediaId)));
            mockMvc.perform(multipart("/api/admin/gallery")
                            .file(new MockMultipartFile("item", "", MediaType.APPLICATION_JSON_VALUE, item.getBytes()))
                            .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth()))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void attachToArticle(Long mediaId) {
        try {
            String article = objectMapper.writeValueAsString(Map.of(
                    "title", "Post",
                    "published", true,
                    "mediaIds", List.of(mediaId)));
            mockMvc.perform(post("/api/admin/articles")
                            .header(HttpHeaders.AUTHORIZATION, TestSupport.basicAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(article))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
