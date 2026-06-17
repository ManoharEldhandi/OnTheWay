package com.ontheway.repository;

import com.ontheway.model.Merchant;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    /** All shops owned by a user (one owner can operate many shops). */
    List<Merchant> findByUser_UserId(Long userId);

    /** Shops in a given status (e.g. the admin's pending-approval queue). */
    List<Merchant> findByStatus(MerchantStatus status);

    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);

    long countByStatus(MerchantStatus status);

    /** Located, publicly visible shops — candidates for geo discovery. */
    List<Merchant> findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(MerchantStatus status);

    /** Located, publicly visible shops of a given vertical/category. */
    List<Merchant> findByStatusAndStoreTypeAndLatitudeIsNotNullAndLongitudeIsNotNull(
            MerchantStatus status, StoreType storeType);
}
