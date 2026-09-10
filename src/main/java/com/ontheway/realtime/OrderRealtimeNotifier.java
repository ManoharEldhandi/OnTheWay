package com.ontheway.realtime;

import com.ontheway.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class OrderRealtimeNotifier {
    private final OrderWebSocketHandler handler;
    private final ObjectProvider<KafkaOrderEventPublisher> kafkaPublisher;

    public void publish(String type, Order order) {
        OrderRealtimeEvent event = new OrderRealtimeEvent(
                type,
                order.getOrderId(),
                order.getUser().getUserId(),
                order.getMerchant().getMerchantId(),
                order.getMerchant().getUser().getUserId(),
                order.getStatus(),
                order.getPickupTime(),
                order.getEtaSegment()
        );

        // A receiving tab reloads the order as soon as it sees this event. Publishing
        // before the surrounding write has committed can therefore return stale ETA or
        // status data. Snapshot the event now, then deliver it after a successful commit.
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(event);
                }
            });
            return;
        }
        dispatch(event);
    }

    private void dispatch(OrderRealtimeEvent event) {
        handler.broadcast(event);
        kafkaPublisher.ifAvailable(publisher -> publisher.publish(event));
    }
}
