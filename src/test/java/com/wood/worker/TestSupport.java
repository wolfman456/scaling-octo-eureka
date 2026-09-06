package com.wood.worker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TestSupport {

    public static final String ADMIN_USER = "admin";
    public static final String ADMIN_PASSWORD = "test";

    public static final byte[] PNG_BYTES = "fake-png-content".getBytes(StandardCharsets.UTF_8);

    public static String basicAuth() {
        String raw = ADMIN_USER + ":" + ADMIN_PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private TestSupport() {
    }
}