package com.wood.worker.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebConfigTest {

    @Test
    void parsesSpaceSeparatedAndTrimsEntries() {
        assertEquals(List.of("https://a.com", "https://b.com"),
                WebConfig.parseAllowedOrigins(" https://a.com , https://b.com ,https://a.com "));
    }

    @Test
    void dropsBlankEntries() {
        assertEquals(List.of("https://a.com"),
                WebConfig.parseAllowedOrigins("https://a.com,, ,"));
    }

    @Test
    void emptyInputYieldsEmptyList() {
        assertEquals(List.of(), WebConfig.parseAllowedOrigins(""));
    }
}