package com.wood.worker.service;

import com.wood.worker.StubOpenAiServer;
import com.wood.worker.dto.ArticleDraftDto;
import com.wood.worker.model.MediaAsset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiServiceTest {

    @TempDir
    Path tempDir;

    private StubOpenAiServer server;
    private ObjectMapper mapper;
    private MediaStorageService storage;
    private OpenAiService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubOpenAiServer();
        server.start();
        mapper = new ObjectMapper();
        storage = new MediaStorageService(tempDir.toString());
        service = service("sk-test");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private OpenAiService service(String apiKey) {
        return new OpenAiService(apiKey, server.baseUrl(), "gpt-4o-mini",
                RestClient.builder(), mapper, storage);
    }

    private void respondContent(String content) {
        server.respond(envelope(content));
    }

    private String envelope(String content) {
        ObjectNode message = mapper.createObjectNode().put("role", "assistant").put("content", content);
        ObjectNode root = mapper.createObjectNode();
        root.set("choices", mapper.createArrayNode().add(mapper.createObjectNode().set("message", message)));
        return mapper.writeValueAsString(root);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", "png-bytes".getBytes());
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void disabledWhenKeyBlank() {
        OpenAiService blank = service("   ");
        assertFalse(blank.isEnabled());
        assertThrows(AiDisabledException.class, () -> blank.describeImage(image()));
        assertThrows(AiDisabledException.class, () -> blank.draftArticle("topic", List.of()));
    }

    @Test
    void describeImageReturnsTrimmedCaption() {
        respondContent("  A hand-built oak side table.  ");
        assertEquals("A hand-built oak side table.", service.describeImage(image()));
        assertEquals("Bearer sk-test", server.lastAuthorization());
        assertTrue(server.lastRequestBody().contains("\"role\":\"user\""));
        assertTrue(server.lastRequestBody().contains("image_url"));
        assertTrue(server.lastRequestBody().contains("data:image/png;base64"));
        assertTrue(server.lastRequestBody().contains("gpt-4o-mini"));
    }

    @Test
    void describeImageTruncatesVeryLongCaptions() {
        respondContent("x".repeat(600));
        String result = service.describeImage(image());
        assertEquals(500, result.length());
        assertTrue(result.endsWith("..."));
    }

    @Test
    void describeImageDefaultsJpegWhenContentTypeMissing() {
        respondContent("cap");
        MockMultipartFile noType = new MockMultipartFile("file", "photo", null, "bytes".getBytes());
        service.describeImage(noType);
        assertTrue(server.lastRequestBody().contains("data:image/jpeg;base64"));
    }

    @Test
    void describeImageThrowsWhenReadFails() {
        MultipartFile broken = new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "p.png";
            }

            @Override
            public String getContentType() {
                return "image/png";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 4;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("boom");
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void transferTo(Path dest) {
            }

            @Override
            public void transferTo(java.io.File dest) {
            }
        };
        assertThrows(AiCallException.class, () -> service.describeImage(broken));
    }

    @Test
    void describeImageThrowsOnUpstreamError() {
        server.respond(500, "{\"error\":\"boom\"}");
        assertThrows(AiCallException.class, () -> service.describeImage(image()));
    }

    @Test
    void describeImageThrowsWhenContentMissing() {
        respondContent("");
        assertThrows(AiCallException.class, () -> service.describeImage(image()));
    }

    @Test
    void describeImageThrowsWhenServerUnreachable() {
        server.stop();
        assertThrows(AiCallException.class, () -> service.describeImage(image()));
    }

    @Test
    void draftArticleParsesTitleAndBody() {
        respondContent("{\"title\":\"Oak Table\",\"bodyMd\":\"# Oak Table\\n\\nBuilt from white oak.\"}");
        ArticleDraftDto draft = service.draftArticle("a solid oak table", List.of());
        assertEquals("Oak Table", draft.title());
        assertEquals("# Oak Table\n\nBuilt from white oak.", draft.bodyMd());
        assertTrue(server.lastRequestBody().contains("\"response_format\""));
        assertTrue(server.lastRequestBody().contains("about: a solid oak table"));
        assertFalse(server.lastRequestBody().contains("image_url"));
    }

    @Test
    void draftArticleNullTopicOmitsTopicSentence() {
        respondContent("{\"title\":\"T\",\"bodyMd\":\"B\"}");
        service.draftArticle(null, List.of());
        assertFalse(server.lastRequestBody().contains("about:"));
    }

    @Test
    void draftArticleBlankTopicOmitsTopicSentence() {
        respondContent("{\"title\":\"T\",\"bodyMd\":\"B\"}");
        service.draftArticle("   ", List.of());
        assertFalse(server.lastRequestBody().contains("about:"));
    }

    @Test
    void draftArticleAttachesPhotos() {
        respondContent("{\"title\":\"T\",\"bodyMd\":\"B\"}");
        MediaAsset one = storage.store(new MockMultipartFile("file", "a.png", "image/png", "bytes-a".getBytes()), 0);
        MediaAsset two = storage.store(new MockMultipartFile("file", "b.png", "image/png", "bytes-b".getBytes()), 0);
        service.draftArticle("table", List.of(one, two));
        assertEquals(2, occurrences(server.lastRequestBody(), "data:image/png;base64"));
    }

    @Test
    void draftArticleSkipsNullPhotosAndCapsAtFour() {
        respondContent("{\"title\":\"T\",\"bodyMd\":\"B\"}");
        List<MediaAsset> photos = new java.util.ArrayList<>();
        photos.add(null);
        for (int i = 0; i < 5; i++) {
            photos.add(storage.store(new MockMultipartFile("file", "p" + i + ".png", "image/png", "bytes".getBytes()), 0));
        }
        service.draftArticle("table", photos);
        assertEquals(4, occurrences(server.lastRequestBody(), "data:image/png;base64"));
    }

    @Test
    void draftArticleUsesJpegForAssetWithoutContentType() {
        respondContent("{\"title\":\"T\",\"bodyMd\":\"B\"}");
        MediaAsset asset = storage.store(new MockMultipartFile("file", "x", null, "bytes".getBytes()), 0);
        service.draftArticle("t", List.of(asset));
        assertTrue(server.lastRequestBody().contains("data:image/jpeg;base64"));
    }

    @Test
    void draftArticleThrowsWhenJsonInvalid() {
        respondContent("not json");
        assertThrows(AiCallException.class, () -> service.draftArticle("t", List.of()));
    }

    @Test
    void draftArticleThrowsWhenTitleOrBodyMissing() {
        respondContent("{}");
        assertThrows(AiCallException.class, () -> service.draftArticle("t", List.of()));
    }

    @Test
    void draftArticleThrowsWhenPhotoFileUnreadable() {
        respondContent("{\"title\":\"T\",\"bodyMd\":\"B\"}");
        MediaAsset ghost = new MediaAsset();
        ghost.setStoredName("missing.png");
        ghost.setContentType("image/png");
        assertThrows(AiCallException.class, () -> service.draftArticle("t", List.of(ghost)));
    }
}