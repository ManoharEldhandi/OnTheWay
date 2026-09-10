-- Customer ↔ merchant conversation is an order-scoped, immutable audit trail.
CREATE TABLE order_messages (
    order_message_id BIGINT       NOT NULL AUTO_INCREMENT,
    order_id         BIGINT       NOT NULL,
    sender_user_id   BIGINT       NOT NULL,
    body             VARCHAR(600) NOT NULL,
    created_at       DATETIME     NOT NULL,
    CONSTRAINT pk_order_messages PRIMARY KEY (order_message_id),
    CONSTRAINT fk_order_messages_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_order_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users (user_id)
);

CREATE INDEX idx_order_messages_order_created ON order_messages (order_id, created_at);
