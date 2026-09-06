package com.wood.worker.service;

import com.wood.worker.dto.SettingsUpdateForm;
import com.wood.worker.dto.SiteSettingsDto;
import com.wood.worker.model.SiteSetting;
import com.wood.worker.repository.MediaAssetRepository;
import com.wood.worker.repository.SiteSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SiteSettingsService {

    public static final String SITE_TITLE = "site_title";
    public static final String BACKGROUND_MEDIA = "background_media_id";
    public static final String CONTACT_EMAIL = "contact_email";
    public static final String ETSY_URL = "etsy_url";
    public static final String INSTAGRAM_URL = "instagram_url";
    public static final String FACEBOOK_URL = "facebook_url";

    private final SiteSettingRepository settings;
    private final MediaAssetRepository media;

    public SiteSettingsService(SiteSettingRepository settings, MediaAssetRepository media) {
        this.settings = settings;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public SiteSettingsDto current() {
        Map<String, String> values = settings.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(SiteSetting::getKey, SiteSetting::getValue));
        String backgroundImage = null;
        Long backgroundMediaId = parseId(values.get(BACKGROUND_MEDIA));
        if (backgroundMediaId != null) {
            backgroundImage = media.findById(backgroundMediaId)
                    .map(asset -> "/uploads/" + asset.getStoredName())
                    .orElse(null);
        }
        return new SiteSettingsDto(
                values.getOrDefault(SITE_TITLE, "Six Kids Crafts"),
                backgroundMediaId,
                backgroundImage,
                values.get(CONTACT_EMAIL),
                values.get(ETSY_URL),
                values.get(INSTAGRAM_URL),
                values.get(FACEBOOK_URL));
    }

    @Transactional
    public void update(SettingsUpdateForm form) {
        upsert(SITE_TITLE, form.siteTitle());
        upsert(BACKGROUND_MEDIA, form.backgroundMediaId() == null ? null : String.valueOf(form.backgroundMediaId()));
        upsert(CONTACT_EMAIL, form.contactEmail());
        upsert(ETSY_URL, form.etsyUrl());
        upsert(INSTAGRAM_URL, form.instagramUrl());
        upsert(FACEBOOK_URL, form.facebookUrl());
    }

    private void upsert(String key, String value) {
        if (value == null) {
            return;
        }
        if (value.trim().isEmpty()) {
            if (settings.existsById(key)) {
                settings.deleteById(key);
            }
            return;
        }
        SiteSetting setting = settings.findById(key).orElseGet(() -> {
            SiteSetting created = new SiteSetting();
            created.setKey(key);
            return created;
        });
        setting.setValue(value.trim());
        settings.save(setting);
    }

    public static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}