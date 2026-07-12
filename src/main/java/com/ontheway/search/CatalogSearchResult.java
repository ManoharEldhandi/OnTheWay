package com.ontheway.search;

/** A stable API projection shared by the GraphQL and search backends. */
public record CatalogSearchResult(
        Long itemId,
        String name,
        Long shopId,
        String shopName,
        String vertical,
        Double price,
        String currency,
        boolean available
) {
}
