package com.ontheway.repository;

import com.ontheway.model.MenuItem;
import com.ontheway.model.enums.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByMerchantMerchantId(Long merchantId);
    List<MenuItem> findByAvailabilityTrue();

    /**
     * Available items at approved shops whose item name OR owning shop name contains the query
     * (case-insensitive). Used by the cross-shop product search.
     */
    @Query("""
            SELECT mi FROM MenuItem mi
            JOIN mi.merchant m
            WHERE m.status = :status
              AND mi.availability = true
              AND (LOWER(mi.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.storeName) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    List<MenuItem> searchAvailableByItemOrShopName(@Param("status") MerchantStatus status,
                                                   @Param("q") String query);
}
