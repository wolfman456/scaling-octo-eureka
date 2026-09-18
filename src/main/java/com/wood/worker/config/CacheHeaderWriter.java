package com.wood.worker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/**
 * Cache policy for the API. Uploaded media is served from immutable
 * content-addressed filenames (UUIDs), so it can be cached forever; every other
 * response keeps the defensive no-store policy Spring Security applies by default.
 */
public class CacheHeaderWriter implements HeaderWriter {

    public static final String IMMUTABLE = "public, max-age=31536000, immutable";
    public static final String NO_STORE = "no-cache, no-store, max-age=0, must-revalidate";

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/uploads/")) {
            response.setHeader("Cache-Control", IMMUTABLE);
        } else {
            response.setHeader("Cache-Control", NO_STORE);
        }
    }
}
