package com.wood.worker.controller;

import com.wood.worker.dto.SiteSettingsDto;
import com.wood.worker.service.SiteSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SiteSettingsService settings;

    public SettingsController(SiteSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SiteSettingsDto current() {
        return settings.current();
    }
}