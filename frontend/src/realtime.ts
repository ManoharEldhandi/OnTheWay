import { useEffect } from 'react';
import { getToken } from './api/client';

export interface OrderRealtimeEvent {
  type: string;
  orderId: number;
  userId: number;
  merchantId: number;
  merchantOwnerId: number;
  status: string;
  pickupTime: string;
  etaSegment: string | null;
}

export function useOrderRealtime(onEvent: (event: OrderRealtimeEvent) => void): void {
  useEffect(() => {
    const token = getToken();
    if (!token || typeof WebSocket === 'undefined') return;

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/orders?token=${encodeURIComponent(token)}`);
    socket.onmessage = (message) => {
      try {
        onEvent(JSON.parse(message.data) as OrderRealtimeEvent);
      } catch {
        // Ignore malformed realtime messages; HTTP reload remains available on navigation.
      }
    };
    return () => socket.close();
  }, [onEvent]);
}
