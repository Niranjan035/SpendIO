package com.spendio.categories.dto.response;

public record CategoryTreeResponse(CategoryResponse category, java.util.List<CategoryTreeResponse> children) { }