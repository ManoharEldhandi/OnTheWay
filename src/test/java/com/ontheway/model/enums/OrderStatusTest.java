package com.ontheway.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the order status state machine. No Spring context required.
 */
class OrderStatusTest {

    @Test
    void placedCanGoToPreparingOrCancelled() {
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.PREPARING)).isTrue();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void preparingCanGoToReadyOrCancelled() {
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY)).isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void readyCanOnlyGoToPicked() {
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.PICKED)).isTrue();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.PREPARING)).isFalse();
    }

    @Test
    void illegalAndBackwardTransitionsAreRejected() {
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.READY)).isFalse();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.PICKED)).isFalse();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.PLACED)).isFalse();
        assertThat(OrderStatus.PLACED.canTransitionTo(null)).isFalse();
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        assertThat(OrderStatus.PICKED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.PICKED.canTransitionTo(OrderStatus.PLACED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PREPARING)).isFalse();
    }
}
