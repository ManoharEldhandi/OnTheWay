package com.ontheway.repository;

import com.ontheway.model.Merchant;
import com.ontheway.model.enums.StoreType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    Optional<Merchant> findByUser_UserId(Long userId);

    /** Stores that have a location set — candidates for geo discovery. */
    List<Merchant> findByLatitudeIsNotNullAndLongitudeIsNotNull();

    /** Located stores of a given vertical/category. */
    List<Merchant> findByStoreTypeAndLatitudeIsNotNullAndLongitudeIsNotNull(StoreType storeType);
}
