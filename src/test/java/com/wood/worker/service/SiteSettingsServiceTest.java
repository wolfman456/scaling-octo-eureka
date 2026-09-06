package com.wood.worker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SiteSettingsServiceTest {

    @Test
    void parsesValidId() {
        assertEquals(42L, SiteSettingsService.parseId("  42 "));
    }

    @Test
    void parsesNullToNull() {
        assertNull(SiteSettingsService.parseId(null));
    }

    @Test
    void parsesBlankToNull() {
        assertNull(SiteSettingsService.parseId("  "));
    }

    @Test
    void parsesMalformedToNull() {
        assertNull(SiteSettingsService.parseId("twelve"));
    }
}