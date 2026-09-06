package com.wood.worker.controller;

import com.wood.worker.dto.SettingsUpdateForm;
import com.wood.worker.dto.SiteSettingsDto;
import com.wood.worker.service.SiteSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final SiteSettingsService settings;

    public AdminSettingsController(SiteSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SiteSettingsDto get() {
        return settings.current();
    }

    @PutMapping
    public ResponseEntity<SiteSettingsDto> update(@RequestBody SettingsUpdateForm form) {
        settings.update(form);
        return ResponseEntity.ok(settings.current());
    }
}