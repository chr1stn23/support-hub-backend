package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}