package com.spendio.sharedkernel.web;

import java.util.Collection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds {@link Pageable} instances with the API defaults: 0-based page,
 * size capped at 100, whitelist-validated sort (ARCHITECTURE.md E.1).
 */
public final class PageRequestFactory {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequestFactory() {
    }

    public static Pageable of(Integer page, Integer size) {
        return PageRequest.of(page(page), pageSize(size), Sort.unsorted());
    }

    public static Pageable of(Integer page, Integer size, String sort, Collection<String> allowedSortProperties) {
        return PageRequest.of(page(page), pageSize(size), SortRequest.parse(sort, allowedSortProperties).toSort());
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(page(page), pageSize(size), sort);
    }

    private static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
