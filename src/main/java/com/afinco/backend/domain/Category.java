package com.afinco.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "color_code", nullable = false, length = 7)
    private String colorCode;

    public Category(String name, String colorCode) {
        this.name = requireText(name);
        this.colorCode = requireHexColor(colorCode);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        return value.trim();
    }

    private static String requireHexColor(String value) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Color code must use the #RRGGBB format");
        }
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}
