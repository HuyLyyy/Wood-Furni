package com.woodfurni.catalog.category.service;

import com.woodfurni.catalog.category.dto.CategoryRequest;
import com.woodfurni.catalog.category.dto.CategoryResponse;
import com.woodfurni.catalog.category.dto.CategoryTreeResponse;
import com.woodfurni.catalog.category.model.Category;
import com.woodfurni.catalog.category.model.CategoryStatus;
import com.woodfurni.catalog.category.repository.CategoryRepository;
import com.woodfurni.common.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryTreeResponse> getAllAsTree() {
        List<Category> allCategories = categoryRepository.findAll();
        List<Category> rootCategories = allCategories.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());

        return rootCategories.stream()
                .map(root -> buildTree(root, allCategories))
                .collect(Collectors.toList());
    }

    private CategoryTreeResponse buildTree(Category category, List<Category> allCategories) {
        List<Category> children = allCategories.stream()
                .filter(c -> category.getId().equals(c.getParentId()))
                .collect(Collectors.toList());

        List<CategoryTreeResponse> childNodes = children.stream()
                .map(child -> buildTree(child, allCategories))
                .collect(Collectors.toList());

        return CategoryTreeResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .environment(category.getEnvironment() != null ? category.getEnvironment().name() : null)
                .order(category.getOrder())
                .children(childNodes.isEmpty() ? null : childNodes)
                .build();
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse getById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));
        return toResponse(category);
    }

    public CategoryResponse create(CategoryRequest request) {
        if (request.getSlug() == null || request.getSlug().isBlank()) {
            request.setSlug(generateSlug(request.getName()));
        }

        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug already exists: " + request.getSlug());
        }

        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            if (!categoryRepository.existsById(request.getParentId())) {
                throw new IllegalArgumentException("Parent category not found: " + request.getParentId());
            }
        }

        Category category = Category.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .environment(request.getEnvironment())
                .parentId(request.getParentId())
                .order(request.getOrder() != null ? request.getOrder() : 0)
                .status(request.getStatus() != null ? request.getStatus() : CategoryStatus.ACTIVE)
                .build();

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    public CategoryResponse update(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));

        if (request.getSlug() != null && !request.getSlug().equals(category.getSlug())) {
            if (categoryRepository.existsBySlug(request.getSlug())) {
                throw new IllegalArgumentException("Slug already exists: " + request.getSlug());
            }
            category.setSlug(request.getSlug());
        }

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new IllegalArgumentException("Category cannot be its own parent");
            }
            if (!request.getParentId().isBlank() && !categoryRepository.existsById(request.getParentId())) {
                throw new IllegalArgumentException("Parent category not found: " + request.getParentId());
            }
            category.setParentId(request.getParentId().isBlank() ? null : request.getParentId());
        }

        if (request.getName() != null) {
            category.setName(request.getName());
        }
        if (request.getEnvironment() != null) {
            category.setEnvironment(request.getEnvironment());
        }
        if (request.getOrder() != null) {
            category.setOrder(request.getOrder());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    public void delete(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new EntityNotFoundException("Category not found with id: " + id);
        }

        long childCount = categoryRepository.findByParentId(id).size();
        if (childCount > 0) {
            throw new IllegalArgumentException("Cannot delete category with children. Remove children first.");
        }

        categoryRepository.deleteById(id);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .environment(category.getEnvironment())
                .parentId(category.getParentId())
                .order(category.getOrder())
                .status(category.getStatus())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("đ", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
