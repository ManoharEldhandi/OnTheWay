package com.ontheway.search;

import com.ontheway.model.MenuItem;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ontheway.search.elasticsearch",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class JpaCatalogSearchService implements CatalogSearchService {
    private final MenuItemRepository menuItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogSearchResult> search(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        List<MenuItem> items = normalized.isEmpty()
                ? menuItemRepository.findByAvailabilityTrue()
                : menuItemRepository.searchAvailableByItemOrShopName(MerchantStatus.APPROVED, normalized);
        return items.stream().limit(boundedLimit(limit)).map(JpaCatalogSearchService::toResult).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long reindex() {
        // The relational fallback is already the source of truth; report its searchable size.
        return menuItemRepository.findByAvailabilityTrue().size();
    }

    @Override
    public String backend() {
        return "mysql-jpa";
    }

    static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
    }

    static CatalogSearchResult toResult(MenuItem item) {
        return new CatalogSearchResult(
                item.getMenuItemId(),
                item.getName(),
                item.getMerchant().getMerchantId(),
                item.getMerchant().getStoreName(),
                item.getMerchant().getStoreType().name(),
                item.getPrice(),
                item.getCurrency(),
                Boolean.TRUE.equals(item.getAvailability()));
    }
}
