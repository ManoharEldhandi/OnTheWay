import { useEffect, useRef } from 'react';
import { getToken, refreshAccessToken } from './api/client';

export type RealtimeState = 'offline' | 'connecting' | 'connected' | 'reconnecting';

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

type Subscriber = {
  onEvent: (event: OrderRealtimeEvent) => void;
  onStateChange?: (state: RealtimeState) => void;
};

// One browser tab needs one authenticated socket, not one socket for the layout, order page,
// chat, and queue individually. This module-level hub fans that stream out to React subscribers.
const subscribers = new Map<symbol, Subscriber>();
let socket: WebSocket | null = null;
let retryTimer: number | undefined;
let rotationTimer: number | undefined;
let connectionState: RealtimeState = 'offline';

function notifyState(next: RealtimeState) {
  connectionState = next;
  subscribers.forEach((subscriber) => subscriber.onStateChange?.(next));
}

function clearTimers() {
  if (retryTimer !== undefined) window.clearTimeout(retryTimer);
  if (rotationTimer !== undefined) window.clearTimeout(rotationTimer);
  retryTimer = undefined;
  rotationTimer = undefined;
}

function scheduleReconnect() {
  if (retryTimer !== undefined || subscribers.size === 0 || !getToken()) return;
  notifyState('reconnecting');
  retryTimer = window.setTimeout(() => {
    retryTimer = undefined;
    connect();
  }, 1200);
}

function connect() {
  if (typeof WebSocket === 'undefined') {
    notifyState('offline');
    return;
  }
  if (subscribers.size === 0) return;
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;

  const token = getToken();
  if (!token) {
    notifyState('offline');
    return;
  }

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  notifyState(connectionState === 'connected' || connectionState === 'reconnecting' ? 'reconnecting' : 'connecting');
  const nextSocket = new WebSocket(
    `${protocol}://${window.location.host}/ws/orders?token=${encodeURIComponent(token)}`,
  );
  socket = nextSocket;

  nextSocket.onopen = () => {
    if (socket !== nextSocket) return;
    notifyState('connected');
    rotationTimer = window.setTimeout(async () => {
      try {
        await refreshAccessToken();
      } finally {
        // The close handler reconnects with the rotated token (or marks the connection offline).
        nextSocket.close(4000, 'Rotating access token');
      }
    }, 10 * 60 * 1000);
  };
  nextSocket.onmessage = (message) => {
    try {
      const event = JSON.parse(message.data) as OrderRealtimeEvent;
      subscribers.forEach((subscriber) => subscriber.onEvent(event));
    } catch {
      // A malformed event cannot disturb the existing connection or the HTTP fallback.
    }
  };
  nextSocket.onerror = () => nextSocket.close();
  nextSocket.onclose = () => {
    if (socket !== nextSocket) return;
    socket = null;
    if (rotationTimer !== undefined) window.clearTimeout(rotationTimer);
    rotationTimer = undefined;
    if (subscribers.size === 0 || !getToken()) {
      notifyState('offline');
      return;
    }
    scheduleReconnect();
  };
}

function disconnectWhenUnused() {
  if (subscribers.size !== 0) return;
  clearTimers();
  const activeSocket = socket;
  socket = null;
  if (activeSocket && activeSocket.readyState === WebSocket.OPEN) activeSocket.close(1000, 'No active subscribers');
  notifyState('offline');
}

/**
 * Subscribe a component to the tab's single authenticated order-event stream. The server filters
 * events by participant, so separate customer and merchant tabs receive their shared order's
 * timing events without sharing location data.
 */
export function useOrderRealtime(
  onEvent: (event: OrderRealtimeEvent) => void,
  onStateChange?: (state: RealtimeState) => void,
): void {
  const eventRef = useRef(onEvent);
  const stateRef = useRef(onStateChange);
  eventRef.current = onEvent;
  stateRef.current = onStateChange;

  useEffect(() => {
    const id = Symbol('realtime-subscriber');
    const subscriber: Subscriber = {
      onEvent: (event) => eventRef.current(event),
      onStateChange: (state) => stateRef.current?.(state),
    };
    subscribers.set(id, subscriber);
    subscriber.onStateChange?.(connectionState);
    connect();

    return () => {
      subscribers.delete(id);
      disconnectWhenUnused();
    };
  }, []);
}
