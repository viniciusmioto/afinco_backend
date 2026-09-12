package com.afinco.backend.mapper;

import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getExpenseType(), category.getColorCode());
    }
}
