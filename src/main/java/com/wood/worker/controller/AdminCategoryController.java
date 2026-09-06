package com.wood.worker.controller;

import com.wood.worker.dto.CategoryDto;
import com.wood.worker.dto.CategoryForm;
import com.wood.worker.model.Category;
import com.wood.worker.repository.CategoryRepository;
import com.wood.worker.repository.GalleryItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryRepository categories;
    private final GalleryItemRepository galleryItems;

    public AdminCategoryController(CategoryRepository categories, GalleryItemRepository galleryItems) {
        this.categories = categories;
        this.galleryItems = galleryItems;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryDto> list() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryForm form) {
        Category category = new Category();
        apply(category, form);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryDto.from(categories.save(category)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<CategoryDto> update(@PathVariable Long id, @RequestBody CategoryForm form) {
        return categories.findById(id)
                .map(category -> {
                    apply(category, form);
                    return ResponseEntity.ok(CategoryDto.from(categories.save(category)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return categories.findById(id)
                .map(category -> {
                    if (galleryItems.countByCategoryId(id) > 0) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).<Void>build();
                    }
                    categories.delete(category);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void apply(Category category, CategoryForm form) {
        category.setName(form.name());
        category.setSortOrder(form.sortOrder());
    }
}