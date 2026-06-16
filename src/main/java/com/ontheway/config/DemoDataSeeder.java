package com.ontheway.config;

import com.ontheway.model.*;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds a realistic, multi-vertical demo dataset on startup when
 * {@code ontheway.seed.enabled=true} (the {@code demo} profile). Idempotent: it does
 * nothing if any users already exist.
 *
 * <p>Demo logins (all password {@code password123}):
 * <ul>
 *   <li>admin@ontheway.app — ADMIN</li>
 *   <li>alice@ontheway.app — USER (customer)</li>
 *   <li>biryani@ontheway.app, medplus@ontheway.app, cafe@ontheway.app — MERCHANT</li>
 * </ul>
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
        if (userRepository.count() > 0) {
            log.info("Demo seed skipped: data already present.");
            return;
        }
        log.info("Seeding demo data...");

        createUser("admin@ontheway.app", "Ava Admin", UserRole.ADMIN);
        createUser("alice@ontheway.app", "Alice Customer", UserRole.USER);

        seedStore("biryani@ontheway.app", "Bangalore Biryani House", StoreType.RESTAURANT,
                "MG Road, Bengaluru", 12.9716, 77.5946, 20, List.of(
                        item("Chicken Biryani", "Aromatic dum biryani", 250.0),
                        item("Paneer Butter Masala", "Creamy and rich", 220.0),
                        item("Veg Pulao", "Fragrant rice with veggies", 180.0)));

        seedStore("medplus@ontheway.app", "MedPlus Pharmacy", StoreType.PHARMACY,
                "Indiranagar, Bengaluru", 12.9750, 77.6000, 10, List.of(
                        item("Paracetamol 500mg (10)", "Fever & pain relief", 30.0),
                        item("Vitamin C (60)", "Immunity booster", 150.0),
                        item("Hand Sanitizer 200ml", "70% alcohol", 90.0)));

        seedStore("cafe@ontheway.app", "Brew & Bite Cafe", StoreType.CAFE,
                "Koramangala, Bengaluru", 12.9680, 77.5900, 8, List.of(
                        item("Cappuccino", "Freshly brewed", 120.0),
                        item("Veg Sandwich", "Grilled, toasted", 100.0),
                        item("Blueberry Muffin", "Baked today", 80.0)));

        log.info("Demo data seeded: {} users, {} merchants, {} menu items.",
                userRepository.count(), merchantRepository.count(), menuItemRepository.count());
    }

    private User createUser(String email, String name, UserRole role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .name(name)
                .role(role)
                .build());
    }

    private void seedStore(String email, String storeName, StoreType type, String address,
                           double lat, double lng, int prepMins, List<MenuItem> items) {
        User owner = createUser(email, storeName + " Owner", UserRole.MERCHANT);
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .user(owner)
                .storeName(storeName)
                .storeType(type)
                .address(address)
                .latitude(lat)
                .longitude(lng)
                .prepTimeMins(prepMins)
                .etaBufferMins(5)
                .build());
        for (MenuItem item : items) {
            item.setMerchant(merchant);
            menuItemRepository.save(item);
        }
    }

    private MenuItem item(String name, String description, double price) {
        return MenuItem.builder()
                .name(name).description(description).price(price).availability(true).build();
    }
}
