package com.ontheway.controller;

import com.ontheway.search.CatalogSearchResult;
import com.ontheway.search.CatalogSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogGraphqlControllerTest {

    @Test
    void delegatesAuthenticatedCatalogSearchWithSafeDefaultLimit() {
        CatalogSearchService service = mock(CatalogSearchService.class);
        CatalogSearchResult result = new CatalogSearchResult(
                1L, "Coffee", 2L, "Cafe", "FOOD", 40.0, "INR", true);
        when(service.search("coffee", 20)).thenReturn(List.of(result));

        List<CatalogSearchResult> results = new CatalogGraphqlController(service)
                .catalogSearch("coffee", null);

        assertThat(results).containsExactly(result);
        verify(service).search("coffee", 20);
    }

    @Test
    void exposesBackendAndAdminReindexCount() {
        CatalogSearchService service = mock(CatalogSearchService.class);
        when(service.backend()).thenReturn("elasticsearch");
        when(service.reindex()).thenReturn(507L);
        CatalogGraphqlController controller = new CatalogGraphqlController(service);

        assertThat(controller.catalogSearchBackend()).isEqualTo("elasticsearch");
        assertThat(controller.reindexCatalog()).isEqualTo("507");
    }
}
