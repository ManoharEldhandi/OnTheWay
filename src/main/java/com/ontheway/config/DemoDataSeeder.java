package com.ontheway.config;

import com.ontheway.model.MenuItem;
import com.ontheway.model.Merchant;
import com.ontheway.model.User;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.MenuItemRepository;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.repository.UserRepository;
import com.ontheway.util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A deliberately small, reliable dataset for the zero-setup product walkthrough.
 * Every listed shop is approved, stocked, geographically distinct, and owned by the
 * same demo merchant. That means any pin a customer opens can be accepted and fulfilled
 * from the single merchant dashboard; no decorative or unusable catalogue entries exist.
 *
 * <p>All accounts use {@code password123}:
 * {@code demo.customer@ontheway.app}, {@code demo.merchant@ontheway.app}, and
 * {@code demo.admin@ontheway.app}.
 */
@Component
@ConditionalOnProperty(name = "ontheway.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Ensuring the curated OnTheWay walkthrough data...");

        createUser("demo.customer@ontheway.app", "Demo Customer", UserRole.USER);
        createUser("demo.admin@ontheway.app", "Demo Administrator", UserRole.ADMIN);
        User merchant = createUser("demo.merchant@ontheway.app", "OnTheWay Merchant", UserRole.MERCHANT);

        // The customer default is MG Road. These locations fan out around it, so every
        // route has visible length and every pin remains independently selectable.
        saveShop(merchant, "Route Ready Café", StoreType.CAFE,
                "Indiranagar 100ft Road, Bengaluru", 12.9854, 77.6168, 7, List.of(
                        item("Signature Cappuccino", "Double-shot coffee, made to collect", 119),
                        item("Breakfast Roll", "Egg, greens, and herb mayo", 139),
                        item("Iced Tea", "Fresh lemon tea for the ride", 79)));
        saveShop(merchant, "Saffron Table", StoreType.RESTAURANT,
                "Richmond Road, Bengaluru", 12.9470, 77.6035, 10, List.of(
                        item("Mysore Masala Dosa", "Crisp dosa with coconut chutney", 145),
                        item("Paneer Rice Bowl", "Comfort food for pickup", 219),
                        item("Filter Coffee", "South Indian filter blend", 65)));
        saveShop(merchant, "Green Cross Pharmacy", StoreType.PHARMACY,
                "Benson Town, Bengaluru", 13.0131, 77.6184, 6, List.of(
                        item("Paracetamol 500mg", "Strip of 10 tablets", 32),
                        item("Vitamin C Gummies", "Orange, 30 count", 199),
                        item("Antiseptic Wipes", "Travel pack", 79)));
        saveShop(merchant, "Market Lane General Store", StoreType.GROCERY,
                "Koramangala 5th Block, Bengaluru", 12.9331, 77.6257, 8, List.of(
                        item("Daily Essentials Basket", "Milk, bread, fruit, and eggs", 315),
                        item("Toor Dal 1kg", "Everyday pantry staple", 169),
                        item("Bananas 1kg", "Fresh produce", 68)));
        saveShop(merchant, "Circuit House", StoreType.ELECTRONICS,
                "Malleshwaram, Bengaluru", 12.9970, 77.5675, 9, List.of(
                        item("USB-C Cable", "Durable 1.5 metre cable", 299),
                        item("Power Bank", "10,000 mAh", 1299),
                        item("Wireless Earbuds", "Compact charging case", 1699)));

        log.info("Curated demo data ready: {} accounts, {} orderable shops, {} menu items.",
                userRepository.count(), merchantRepository.count(), menuItemRepository.count());
    }

    private User createUser(String email, String name, UserRole role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .name(name)
                .role(role)
                .build()));
    }

    private void saveShop(User owner, String name, StoreType type, String address,
                          double latitude, double longitude, int prepMins, List<MenuItem> items) {
        Merchant shop = merchantRepository.findByStoreNameIgnoreCase(name).orElseGet(() -> merchantRepository.save(Merchant.builder()
                .user(owner)
                .storeName(name)
                .storeType(type)
                .status(MerchantStatus.APPROVED)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .prepTimeMins(prepMins)
                .etaBufferMins(3)
                .build()));
        if (!menuItemRepository.findByMerchantMerchantId(shop.getMerchantId()).isEmpty()) {
            return;
        }
        for (MenuItem item : items) {
            item.setMerchant(shop);
            menuItemRepository.save(item);
        }
    }

    private MenuItem item(String name, String description, double price) {
        return MenuItem.builder()
                .name(name)
                .description(description)
                .price(price)
                .priceMinor(Money.toMinor(price))
                .currency(Money.DEFAULT_CURRENCY)
                .availability(true)
                .build();
    }
}
