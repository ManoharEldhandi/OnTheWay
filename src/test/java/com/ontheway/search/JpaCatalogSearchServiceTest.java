package com.ontheway.search;

import com.ontheway.model.MenuItem;
import com.ontheway.model.Merchant;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaCatalogSearchServiceTest {

    @Mock private MenuItemRepository menuItemRepository;

    @Test
    void searchUsesRelationalQueryAndMapsSearchableFields() {
        Merchant merchant = Merchant.builder()
                .merchantId(11L)
                .storeName("Campus Cafe")
                .storeType(StoreType.CAFE)
                .status(MerchantStatus.APPROVED)
                .build();
        MenuItem item = MenuItem.builder()
                .menuItemId(21L)
                .merchant(merchant)
                .name("Masala Dosa")
                .price(75.0)
                .currency("INR")
                .availability(true)
                .build();
        when(menuItemRepository.searchAvailableByItemOrShopName(MerchantStatus.APPROVED, "dosa"))
                .thenReturn(List.of(item));

        List<CatalogSearchResult> results = new JpaCatalogSearchService(menuItemRepository)
                .search("  dosa  ", 10);

        assertThat(results).containsExactly(new CatalogSearchResult(
                21L, "Masala Dosa", 11L, "Campus Cafe", "CAFE", 75.0, "INR", true));
    }

    @Test
    void reindexReportsRelationalSourceOfTruthSize() {
        when(menuItemRepository.findByAvailabilityTrue()).thenReturn(List.of(
                MenuItem.builder().menuItemId(1L).build(),
                MenuItem.builder().menuItemId(2L).build()));

        long searchable = new JpaCatalogSearchService(menuItemRepository).reindex();

        assertThat(searchable).isEqualTo(2L);
        verify(menuItemRepository).findByAvailabilityTrue();
    }

    @Test
    void boundedLimitProtectsSearchBackend() {
        assertThat(JpaCatalogSearchService.boundedLimit(-1)).isEqualTo(20);
        assertThat(JpaCatalogSearchService.boundedLimit(0)).isEqualTo(20);
        assertThat(JpaCatalogSearchService.boundedLimit(101)).isEqualTo(100);
    }
}
