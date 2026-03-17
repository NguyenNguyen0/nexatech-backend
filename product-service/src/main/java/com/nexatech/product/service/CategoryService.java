package com.nexatech.product.service;

import com.nexatech.product.dto.CategoryRequest;
import com.nexatech.product.dto.CategoryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    public CategoryResponse createCategory(CategoryRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public CategoryResponse getCategory(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<CategoryResponse> getAllCategories() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<CategoryResponse> getSubCategories(String parentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteCategory(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
