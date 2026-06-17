package com.ontheway.model;

import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long merchantId;

    /**
     * The owner of this shop. One owner can operate many shops, so this is a many-to-one
     * relationship (it was previously one-to-one).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreType storeType;

    /**
     * Marketplace lifecycle status. New shops are created as {@link MerchantStatus#PENDING}
     * and become discoverable only when an administrator approves them.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING;

    /** Reason recorded when an administrator rejects or suspends the shop. */
    @Column(length = 500)
    private String statusReason;

    @Column(nullable = false, length = 300)
    private String address;

    /** Store location (used by the ETA engine and discovery). Nullable for legacy rows. */
    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** Base preparation time in minutes for a typical order at this store. */
    @Column
    private Integer prepTimeMins;

    @Column(nullable = false)
    private Integer etaBufferMins;

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MenuItem> menuItems = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
