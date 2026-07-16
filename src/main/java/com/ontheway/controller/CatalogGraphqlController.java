package com.ontheway.controller;

import com.ontheway.search.CatalogSearchResult;
import com.ontheway.search.CatalogSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CatalogGraphqlController {
    private final CatalogSearchService catalogSearchService;

    @QueryMapping
    @PreAuthorize("hasAnyRole('USER','MERCHANT','ADMIN')")
    public List<CatalogSearchResult> catalogSearch(
            @Argument("query") String query,
            @Argument("limit") Integer limit
    ) {
        return catalogSearchService.search(query, limit == null ? 20 : limit);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('USER','MERCHANT','ADMIN')")
    public String catalogSearchBackend() {
        return catalogSearchService.backend();
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String reindexCatalog() {
        return String.valueOf(catalogSearchService.reindex());
    }
}
