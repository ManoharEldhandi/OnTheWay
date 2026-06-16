-- ===================================================================
-- V2 — Order status audit trail
-- One immutable row per order status transition (including placement).
-- ===================================================================

CREATE TABLE order_events (
    order_event_id BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)  NOT NULL,
    changed_by     VARCHAR(100) NOT NULL,
    reason         VARCHAR(255),
    created_at     DATETIME     NOT NULL,
    CONSTRAINT pk_order_events PRIMARY KEY (order_event_id),
    CONSTRAINT fk_order_events_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE INDEX idx_order_events_order ON order_events (order_id);
