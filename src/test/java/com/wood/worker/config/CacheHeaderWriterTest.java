package com.wood.worker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheHeaderWriterTest {

    private final CacheHeaderWriter writer = new CacheHeaderWriter();

    @Test
    void uploadedMediaIsCachedForever() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/abc.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertEquals(CacheHeaderWriter.IMMUTABLE, response.getHeader("Cache-Control"));
    }

    @Test
    void apiResponsesAreNotCached() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertEquals(CacheHeaderWriter.NO_STORE, response.getHeader("Cache-Control"));
    }

    @Test
    void missingRequestUriIsNotCached() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/") {
            @Override
            public String getRequestURI() {
                return null;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertEquals(CacheHeaderWriter.NO_STORE, response.getHeader("Cache-Control"));
    }
}
