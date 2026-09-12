package com.afinco.backend.api.reference;

import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.service.ReferenceDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final ReferenceDataService referenceDataService;

    public CategoryController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping({"", "/"})
    public List<CategoryResponse> findCategories() {
        return referenceDataService.findCategories();
    }
}
