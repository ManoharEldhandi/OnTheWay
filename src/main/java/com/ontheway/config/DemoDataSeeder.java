package com.ontheway.config;

import com.ontheway.model.*;
import com.ontheway.model.enums.MerchantStatus;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds a realistic, multi-vertical demo dataset on startup when
 * {@code ontheway.seed.enabled=true} (the {@code demo} profile). Idempotent: it does nothing if
 * any users already exist.
 *
 * <p>The dataset includes:
 * <ul>
 *   <li>An administrator and a customer.</li>
 *   <li>A small set of named, approved shops used in the guided walkthrough.</li>
 *   <li>A merchant who owns two shops (to demonstrate multi-shop ownership), including one that is
 *       still pending approval, plus a suspended shop — so the admin console has real moderation
 *       work to show.</li>
 *   <li>Over one hundred generated, approved shops spread across many verticals around the city,
 *       to demonstrate discovery and search at a realistic scale.</li>
 * </ul>
 *
 * <p>Demo logins (all password {@code password123}): {@code admin@ontheway.app} (admin),
 * {@code alice@ontheway.app} (customer), {@code biryani@ontheway.app} (merchant, multi-shop),
 * {@code medplus@ontheway.app} and {@code cafe@ontheway.app} (merchants).
 */
@Component
@ConditionalOnProperty(name = "ontheway.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "password123";

    /** City centre used as the geographic anchor for generated shops (MG Road, Bengaluru). */
    private static final double BASE_LAT = 12.9716;
    private static final double BASE_LNG = 77.5946;

    /** How many additional shops to generate across verticals. */
    private static final int GENERATED_SHOPS = 110;

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;

    private final Random random = new Random(42); // deterministic for reproducible demos

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

        seedNamedDemoShops();
        seedGeneratedShops();

        log.info("Demo data seeded: {} users, {} shops ({} approved, {} pending, {} suspended), {} items.",
                userRepository.count(), merchantRepository.count(),
                merchantRepository.countByStatus(MerchantStatus.APPROVED),
                merchantRepository.countByStatus(MerchantStatus.PENDING),
                merchantRepository.countByStatus(MerchantStatus.SUSPENDED),
                menuItemRepository.count());
    }

    /** A few named shops used by the guided walkthrough, plus admin-moderation examples. */
    private void seedNamedDemoShops() {
        // A merchant who owns multiple shops: one approved, one still pending review.
        User biryaniOwner = createUser("biryani@ontheway.app", "Bengaluru Biryani Co.", UserRole.MERCHANT);
        saveShop(biryaniOwner, "Bangalore Biryani House", StoreType.RESTAURANT, MerchantStatus.APPROVED,
                "MG Road, Bengaluru", 12.9716, 77.5946, 20, null, List.of(
                        item("Chicken Biryani", "Aromatic dum biryani", 250.0),
                        item("Paneer Butter Masala", "Creamy and rich", 220.0),
                        item("Veg Pulao", "Fragrant rice with veggies", 180.0)));
        saveShop(biryaniOwner, "Biryani House Express", StoreType.FAST_FOOD, MerchantStatus.PENDING,
                "Whitefield, Bengaluru", 12.9698, 77.7499, 12, null, List.of(
                        item("Express Chicken Biryani", "Single-serve, fast", 180.0),
                        item("Egg Roll", "Street-style", 70.0)));

        User medOwner = createUser("medplus@ontheway.app", "MedPlus Owner", UserRole.MERCHANT);
        saveShop(medOwner, "MedPlus Pharmacy", StoreType.PHARMACY, MerchantStatus.APPROVED,
                "Indiranagar, Bengaluru", 12.9750, 77.6000, 10, null, List.of(
                        item("Paracetamol 500mg (10)", "Fever and pain relief", 30.0),
                        item("Vitamin C (60)", "Immunity support", 150.0),
                        item("Hand Sanitizer 200ml", "70% alcohol", 90.0)));

        User cafeOwner = createUser("cafe@ontheway.app", "Brew & Bite Owner", UserRole.MERCHANT);
        saveShop(cafeOwner, "Brew & Bite Cafe", StoreType.CAFE, MerchantStatus.APPROVED,
                "Koramangala, Bengaluru", 12.9680, 77.5900, 8, null, List.of(
                        item("Cappuccino", "Freshly brewed", 120.0),
                        item("Veg Sandwich", "Grilled and toasted", 100.0),
                        item("Blueberry Muffin", "Baked today", 80.0)));
        // A suspended shop so the admin console shows a reactivation case.
        saveShop(cafeOwner, "Late Night Diner", StoreType.RESTAURANT, MerchantStatus.SUSPENDED,
                "HSR Layout, Bengaluru", 12.9100, 77.6400, 15, "Temporarily suspended pending review",
                List.of(item("Midnight Thali", "Full meal", 200.0)));
    }

    /** Generates many approved shops across verticals to demonstrate discovery and search at scale. */
    private void seedGeneratedShops() {
        Vertical[] verticals = Vertical.values();
        for (int i = 0; i < GENERATED_SHOPS; i++) {
            Vertical v = verticals[i % verticals.length];
            String ownerEmail = "owner" + i + "@ontheway.app";
            User owner = createUser(ownerEmail, v.label + " Owner " + i, UserRole.MERCHANT);

            String shopName = v.namePrefixes[random.nextInt(v.namePrefixes.length)] + " " + (i + 1);
            double lat = BASE_LAT + (random.nextDouble() - 0.5) * 0.18; // ~±10 km
            double lng = BASE_LNG + (random.nextDouble() - 0.5) * 0.18;
            int prep = 5 + random.nextInt(25);

            List<MenuItem> items = new ArrayList<>();
            int itemCount = 3 + random.nextInt(4);
            for (int k = 0; k < itemCount; k++) {
                String name = v.items[random.nextInt(v.items.length)];
                double price = v.minPrice + random.nextInt(Math.max(1, v.maxPrice - v.minPrice));
                items.add(item(name, v.label + " item", round2(price)));
            }
            saveShop(owner, shopName, v.storeType, MerchantStatus.APPROVED,
                    v.label + " district, Bengaluru", lat, lng, prep, null, items);
        }
    }

    // ----- persistence helpers ------------------------------------------

    private User createUser(String email, String name, UserRole role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .name(name)
                .role(role)
                .build());
    }

    private void saveShop(User owner, String name, StoreType type, MerchantStatus status,
                          String address, double lat, double lng, int prepMins, String statusReason,
                          List<MenuItem> items) {
        Merchant shop = merchantRepository.save(Merchant.builder()
                .user(owner)
                .storeName(name)
                .storeType(type)
                .status(status)
                .statusReason(statusReason)
                .address(address)
                .latitude(lat)
                .longitude(lng)
                .prepTimeMins(prepMins)
                .etaBufferMins(5)
                .build());
        for (MenuItem mi : items) {
            mi.setMerchant(shop);
            menuItemRepository.save(mi);
        }
    }

    private MenuItem item(String name, String description, double price) {
        return MenuItem.builder()
                .name(name).description(description).price(price).availability(true).build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Templates used to generate believable shops, menus, and prices per vertical.
     */
    private enum Vertical {
        RESTAURANTS(StoreType.RESTAURANT, "Restaurant", 120, 400,
                new String[]{"Spice Garden", "Curry Leaf", "Tandoori Nights", "Coastal Kitchen"},
                new String[]{"Chicken Biryani", "Paneer Tikka", "Masala Dosa", "Butter Naan", "Dal Makhani"}),
        FAST_FOOD(StoreType.FAST_FOOD, "Fast Food", 60, 220,
                new String[]{"Burger Point", "Roll Express", "Wrap & Go", "Quick Bites"},
                new String[]{"Veg Burger", "Chicken Wrap", "French Fries", "Egg Roll", "Cold Coffee"}),
        CAFES(StoreType.CAFE, "Cafe", 80, 280,
                new String[]{"Bean Scene", "The Daily Grind", "Cuppa Co", "Mocha House"},
                new String[]{"Cappuccino", "Latte", "Croissant", "Veg Sandwich", "Brownie"}),
        BAKERY(StoreType.BAKERY, "Bakery", 40, 250,
                new String[]{"Fresh Crumbs", "Golden Loaf", "Sweet Tooth", "Oven Fresh"},
                new String[]{"Whole Wheat Bread", "Chocolate Cake", "Puff", "Cookies (250g)", "Muffin"}),
        PHARMACY(StoreType.PHARMACY, "Pharmacy", 20, 400,
                new String[]{"CityCare Pharmacy", "WellMeds", "HealthFirst", "QuickMed"},
                new String[]{"Paracetamol (10)", "Cough Syrup", "Vitamin C (60)", "Bandage Roll", "ORS Pack"}),
        MEDICAL(StoreType.MEDICAL, "Medical", 50, 600,
                new String[]{"LifeLine Medical", "CarePoint", "MediStore", "PrimeHealth"},
                new String[]{"BP Monitor", "Thermometer", "First-Aid Kit", "Glucometer Strips", "Face Masks (50)"}),
        GROCERY(StoreType.GROCERY, "Grocery", 20, 300,
                new String[]{"Daily Needs", "Corner Mart", "FreshKart", "Green Basket"},
                new String[]{"Rice (1kg)", "Toor Dal (1kg)", "Cooking Oil (1L)", "Sugar (1kg)", "Tea (250g)"}),
        SUPERMARKET(StoreType.SUPERMARKET, "Supermarket", 30, 500,
                new String[]{"MegaMart", "ValueStore", "SuperSave", "BigBasket Hub"},
                new String[]{"Detergent (1kg)", "Shampoo (340ml)", "Biscuits Combo", "Atta (5kg)", "Soft Drink (2L)"}),
        HOTEL(StoreType.HOTEL, "Hotel", 200, 1200,
                new String[]{"Comfort Inn", "City Lodge", "Grand Stay", "Rest Easy"},
                new String[]{"Room Service Thali", "Continental Breakfast", "Club Sandwich", "Fresh Juice", "Pasta"}),
        BOOKSTORE(StoreType.BOOKSTORE, "Bookstore", 100, 800,
                new String[]{"Page Turner", "Book Nook", "Readers' Corner", "Inkwell"},
                new String[]{"Fiction Bestseller", "Notebook (200p)", "Gel Pens (5)", "Children's Book", "Magazine"}),
        ELECTRONICS(StoreType.ELECTRONICS, "Electronics", 150, 3000,
                new String[]{"Gadget Hub", "ElectroMart", "TechPoint", "PowerPlay"},
                new String[]{"USB-C Cable", "Earbuds", "Power Bank 10000mAh", "Phone Case", "HDMI Cable"}),
        HARDWARE(StoreType.HARDWARE, "Hardware", 30, 900,
                new String[]{"FixIt Hardware", "ToolBox", "BuildMart", "Nuts & Bolts"},
                new String[]{"Screwdriver Set", "LED Bulb", "Paint (1L)", "Hammer", "Extension Board"}),
        FLORIST(StoreType.FLORIST, "Florist", 100, 700,
                new String[]{"Petal Co", "Bloom Room", "Flower Bazaar", "Fresh Blooms"},
                new String[]{"Rose Bouquet", "Gerbera Bunch", "Orchid Pot", "Birthday Flowers", "Marigold Garland"}),
        PET_STORE(StoreType.PET_STORE, "Pet Store", 60, 1200,
                new String[]{"Paws & Claws", "Pet Corner", "Happy Tails", "FurryFriends"},
                new String[]{"Dog Food (1kg)", "Cat Litter (5kg)", "Chew Toy", "Pet Shampoo", "Bird Feed"}),
        RETAIL(StoreType.RETAIL, "Retail", 100, 1500,
                new String[]{"Style Studio", "Urban Threads", "Trend Mart", "Daily Wear"},
                new String[]{"Cotton T-Shirt", "Socks (3 pairs)", "Cap", "Tote Bag", "Belt"});

        final StoreType storeType;
        final String label;
        final int minPrice;
        final int maxPrice;
        final String[] namePrefixes;
        final String[] items;

        Vertical(StoreType storeType, String label, int minPrice, int maxPrice,
                 String[] namePrefixes, String[] items) {
            this.storeType = storeType;
            this.label = label;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.namePrefixes = namePrefixes;
            this.items = items;
        }
    }
}
