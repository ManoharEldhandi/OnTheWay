package com.ontheway.search;

import java.util.List;

public interface CatalogSearchService {
    List<CatalogSearchResult> search(String query, int limit);

    /** Rebuild or refresh the configured search projection and return indexed records. */
    long reindex();

    String backend();
}
