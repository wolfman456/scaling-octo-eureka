package com.wood.worker.service;

import com.wood.worker.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugServiceTest {

    private final Set<String> existing = new HashSet<>();
    private SlugService service;

    @BeforeEach
    void setUp() {
        ArticleRepository repo = (ArticleRepository) Proxy.newProxyInstance(
                SlugServiceTest.class.getClassLoader(),
                new Class<?>[]{ArticleRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsBySlug") && args != null && args.length == 1) {
                        return existing.contains((String) args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        service = new SlugService(repo);
    }

    @Test
    void lowercasesAndSlugsTitle() {
        assertEquals("my-wooden-table", service.uniqueSlug("My Wooden TABLE"));
    }

    @Test
    void slugsNullTitle() {
        assertEquals("article", service.uniqueSlug(null));
    }

    @Test
    void slugsBlankTitle() {
        assertEquals("article", service.uniqueSlug("   "));
    }

    @Test
    void slugsNonAsciiTitle() {
        assertEquals("ber-tisch", service.uniqueSlug("Über Tisch"));
    }

    @Test
    void slugsTitleWithNoUsableCharacters() {
        assertEquals("article", service.uniqueSlug("!!!"));
    }

    @Test
    void appendsSuffixOnCollision() {
        existing.add("table");
        assertEquals("table-2", service.uniqueSlug("Table"));
    }

    @Test
    void appendsNextFreeSuffixWhenCollidingAtThree() {
        existing.add("table");
        existing.add("table-2");
        assertEquals("table-3", service.uniqueSlug("Table"));
    }
}