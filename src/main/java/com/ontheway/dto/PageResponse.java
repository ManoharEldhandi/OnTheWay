package com.ontheway.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON representation for paginated API responses.
 *
 * <p>Returning Spring Data's {@link Page} implementation directly exposes an internal,
 * version-dependent serialization shape. This DTO keeps the public API contract explicit.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                List.copyOf(source.getContent()),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast(),
                source.hasNext(),
                source.hasPrevious()
        );
    }
}
