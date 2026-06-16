-- ===================================================================
-- V1 — Baseline schema for OnTheWay
-- Mirrors the existing JPA entity model (food-only v1).
-- Written in the portable SQL subset that runs on both MySQL 8 and
-- H2 (MySQL-compatibility mode), so the same migration is exercised
-- by the hermetic test suite and by real deployments.
-- ===================================================================

CREATE TABLE users (
    user_id     BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(100) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE merchants (
    merchant_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    store_name      VARCHAR(150) NOT NULL,
    store_type      VARCHAR(20)  NOT NULL,
    address         VARCHAR(300) NOT NULL,
    eta_buffer_mins INT          NOT NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    CONSTRAINT pk_merchants PRIMARY KEY (merchant_id),
    CONSTRAINT uq_merchants_user UNIQUE (user_id),
    CONSTRAINT fk_merchants_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE menu_items (
    menu_item_id BIGINT       NOT NULL AUTO_INCREMENT,
    merchant_id  BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(500),
    price        DOUBLE       NOT NULL,
    availability BOOLEAN      NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    CONSTRAINT pk_menu_items PRIMARY KEY (menu_item_id),
    CONSTRAINT fk_menu_items_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id)
);

CREATE TABLE orders (
    order_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    merchant_id  BIGINT       NOT NULL,
    order_time   DATETIME     NOT NULL,
    pickup_time  DATETIME     NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    total_amount DOUBLE       NOT NULL,
    eta_segment  VARCHAR(255),
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_orders_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id)
);

CREATE TABLE order_items (
    order_item_id BIGINT   NOT NULL AUTO_INCREMENT,
    order_id      BIGINT   NOT NULL,
    menu_item_id  BIGINT   NOT NULL,
    quantity      INT      NOT NULL,
    price_each    DOUBLE   NOT NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (order_item_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_order_items_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (menu_item_id)
);

CREATE TABLE payments (
    payment_id     BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    payment_status VARCHAR(20)  NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    amount         DOUBLE       NOT NULL,
    payment_time   DATETIME     NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (payment_id),
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE TABLE locations (
    location_id   BIGINT   NOT NULL AUTO_INCREMENT,
    user_id       BIGINT   NOT NULL,
    latitude      DOUBLE   NOT NULL,
    longitude     DOUBLE   NOT NULL,
    recorded_time DATETIME NOT NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT pk_locations PRIMARY KEY (location_id),
    CONSTRAINT fk_locations_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE preferences (
    preference_id    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    veg_non_veg      VARCHAR(10),
    favorite_cuisine VARCHAR(100),
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    CONSTRAINT pk_preferences PRIMARY KEY (preference_id),
    CONSTRAINT uq_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
