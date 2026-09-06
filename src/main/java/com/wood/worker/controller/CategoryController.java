package com.wood.worker.controller;

import com.wood.worker.dto.CategoryDto;
import com.wood.worker.repository.CategoryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryDto> list() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(CategoryDto::from)
                .toList();
    }
}