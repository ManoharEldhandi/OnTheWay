import { useEffect } from 'react';
import { getToken, refreshAccessToken } from './api/client';

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
    if (typeof WebSocket === 'undefined') return;

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    let socket: WebSocket | null = null;
    let retryTimer: number | undefined;
    let rotationTimer: number | undefined;
    let disposed = false;

    const connect = () => {
      const token = getToken();
      if (disposed || !token) return;

      socket = new WebSocket(
        `${protocol}://${window.location.host}/ws/orders?token=${encodeURIComponent(token)}`,
      );
      socket.onopen = () => {
        rotationTimer = window.setTimeout(async () => {
          try {
            if (await refreshAccessToken()) {
              socket?.close(4000, 'Rotating access token');
            } else {
              socket?.close();
            }
          } catch {
            socket?.close();
          }
        }, 10 * 60 * 1000);
      };
      socket.onmessage = (message) => {
        try {
          onEvent(JSON.parse(message.data) as OrderRealtimeEvent);
        } catch {
          // Ignore malformed realtime messages; HTTP reload remains available on navigation.
        }
      };
      socket.onclose = () => {
        if (rotationTimer !== undefined) window.clearTimeout(rotationTimer);
        if (!disposed && getToken()) {
          retryTimer = window.setTimeout(connect, 3000);
        }
      };
      socket.onerror = () => socket?.close();
    };

    connect();
    return () => {
      disposed = true;
      if (retryTimer !== undefined) window.clearTimeout(retryTimer);
      if (rotationTimer !== undefined) window.clearTimeout(rotationTimer);
      if (socket) {
        socket.onclose = null;
        socket.close();
      }
    };
  }, [onEvent]);
}
