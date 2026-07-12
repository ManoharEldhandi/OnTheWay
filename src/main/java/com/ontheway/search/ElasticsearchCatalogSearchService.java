package com.ontheway.search;

import com.ontheway.model.MenuItem;
import com.ontheway.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ontheway.search.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchCatalogSearchService implements CatalogSearchService {
    private final ElasticsearchOperations operations;
    private final MenuItemRepository menuItemRepository;

    @Override
    public List<CatalogSearchResult> search(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> normalized.isEmpty()
                        ? q.matchAll(m -> m)
                        : q.multiMatch(m -> m.query(normalized).fields("name", "shopName", "vertical")))
                .withPageable(PageRequest.of(0, JpaCatalogSearchService.boundedLimit(limit)))
                .build();
        return operations.search(nativeQuery, CatalogSearchDocument.class).stream()
                .map(SearchHit::getContent)
                .map(ElasticsearchCatalogSearchService::toResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long reindex() {
        IndexOperations index = operations.indexOps(CatalogSearchDocument.class);
        if (!index.exists()) {
            index.createWithMapping();
        }
        List<CatalogSearchDocument> documents = menuItemRepository.findByAvailabilityTrue().stream()
                .map(ElasticsearchCatalogSearchService::toDocument)
                .toList();
        operations.save(documents);
        return documents.size();
    }

    @Override
    public String backend() {
        return "elasticsearch";
    }

    static CatalogSearchDocument toDocument(MenuItem item) {
        return CatalogSearchDocument.builder()
                .id(String.valueOf(item.getMenuItemId()))
                .name(item.getName())
                .shopId(item.getMerchant().getMerchantId())
                .shopName(item.getMerchant().getStoreName())
                .vertical(item.getMerchant().getStoreType().name())
                .price(item.getPrice())
                .currency(item.getCurrency())
                .available(Boolean.TRUE.equals(item.getAvailability()))
                .build();
    }

    private static CatalogSearchResult toResult(CatalogSearchDocument item) {
        return new CatalogSearchResult(
                Long.valueOf(item.getId()),
                item.getName(),
                item.getShopId(),
                item.getShopName(),
                item.getVertical(),
                item.getPrice(),
                item.getCurrency(),
                item.isAvailable());
    }
}
