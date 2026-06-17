package com.ontheway.integration;

import com.ontheway.model.Merchant;
import com.ontheway.model.User;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the real Flyway migrations (V1–V6) apply cleanly to a real <b>MySQL</b> database —
 * not just the H2 MySQL-compatibility mode used by the fast test suite. This is the production
 * database engine, so it is the authoritative check that the dialect-specific V6 migration
 * (which drops the one-shop-per-user uniqueness) behaves on MySQL.
 *
 * <p>Requires a Docker daemon, so it is opt-in (it does not run in the default suite). Enable with
 * {@code -Dmysql.it=true}. The container image is pulled on first run.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "mysql.it", matches = "true")
class MySqlMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("onthewaydb")
            .withUsername("ontheway")
            .withPassword("ontheway");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantRepository merchantRepository;

    @Test
    void migrationsApplyOnMySql_andOneOwnerCanHoldMultipleShops() {
        // If the context started, all six migrations applied on real MySQL.
        User owner = userRepository.save(User.builder()
                .name("Multi Owner").email("mysql-owner@x.com")
                .password("x").role(UserRole.MERCHANT).build());

        // V6 removed the unique constraint on merchants.user_id, so two shops for one owner persist.
        merchantRepository.save(Merchant.builder()
                .user(owner).storeName("Shop One").storeType(StoreType.RESTAURANT)
                .address("a1").etaBufferMins(5).status(MerchantStatus.APPROVED).build());
        merchantRepository.save(Merchant.builder()
                .user(owner).storeName("Shop Two").storeType(StoreType.CAFE)
                .address("a2").etaBufferMins(5).status(MerchantStatus.PENDING).build());

        List<Merchant> shops = merchantRepository.findByUser_UserId(owner.getUserId());
        assertThat(shops).hasSize(2);
        assertThat(shops).extracting(Merchant::getStoreName)
                .containsExactlyInAnyOrder("Shop One", "Shop Two");
    }
}
