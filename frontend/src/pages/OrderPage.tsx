import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useOrderRealtime } from '../realtime';
import type { EtaQuote, OrderResponse, OrderStatus } from '../types';

interface PaymentInfo {
  paymentStatus: string;
  gateway: string | null;
  amount: number;
}

const STEPS: OrderStatus[] = ['PLACED', 'PREPARING', 'READY', 'PICKED'];

/** The customer is still travelling while the order is placed or being prepared. */
function isEnRoute(status: OrderStatus): boolean {
  return status === 'PLACED' || status === 'PREPARING';
}

function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function OrderPage() {
  const { orderId } = useParams();
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [payment, setPayment] = useState<PaymentInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [liveEta, setLiveEta] = useState<EtaQuote | null>(null);
  const [tracking, setTracking] = useState(false);
  const [trackError, setTrackError] = useState<string | null>(null);
  const watchId = useRef<number | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api.get<OrderResponse>(`/api/orders/${orderId}`);
      setOrder(data);
      try {
        const pay = await api.get<PaymentInfo>(`/api/payments/order/${orderId}`);
        setPayment(pay);
      } catch {
        setPayment(null);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load order');
    }
  }, [orderId]);

  useEffect(() => {
    void load();
  }, [load]);

  useOrderRealtime(useCallback((event) => {
    if (String(event.orderId) === String(orderId)) {
      void load();
    }
  }, [load, orderId]));

  function stopTracking() {
    if (watchId.current !== null && navigator.geolocation) {
      navigator.geolocation.clearWatch(watchId.current);
    }
    watchId.current = null;
    setTracking(false);
  }

  function startTracking() {
    if (!navigator.geolocation) {
      setTrackError('Live location is not supported by this browser.');
      return;
    }
    setTrackError(null);
    setTracking(true);
    watchId.current = navigator.geolocation.watchPosition(
      async (pos) => {
        try {
          const eta = await api.post<EtaQuote>(`/api/orders/${orderId}/location`, {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          });
          setLiveEta(eta);
          setTrackError(null);
        } catch (err) {
          setTrackError(err instanceof ApiError ? err.message : 'Could not update live ETA');
        }
      },
      () => setTrackError('Location permission denied. Enable it to share your live ETA.'),
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 15000 },
    );
  }

  // Stop streaming once the order is ready/picked/cancelled, and on unmount.
  useEffect(() => {
    if (order && !isEnRoute(order.status) && tracking) {
      stopTracking();
    }
    return () => {
      if (watchId.current !== null && navigator.geolocation) {
        navigator.geolocation.clearWatch(watchId.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order?.status]);

  if (error) return <div className="error">{error}</div>;
  if (!order) return <div className="muted">Loading…</div>;

  const activeIndex = STEPS.indexOf(order.status);
  const cancelled = order.status === 'CANCELLED';
  const enRoute = isEnRoute(order.status);

  return (
    <div className="col">
      <section className="page-head">
        <div className="hero-block motion-line">
          <span className="kicker">Live order / #{order.orderId}</span>
          <h1 className="title">Track the pickup.</h1>
          <p className="sub">Status, payment, and route timing stay in one operational view.</p>
        </div>
        <div className="hero-panel">
          <span className="kicker">Current state</span>
          <div className="value" style={{ fontSize: 42 }}>{order.status}</div>
          <span className={`status ${order.status}`}>{order.status}</span>
        </div>
      </section>

      {payment && (
        <div className="card spread">
          <span className="muted small">Payment</span>
          <span>
            <span className={`badge ${payment.paymentStatus === 'COMPLETED' ? 'ok' : 'warn'}`}>
              {payment.paymentStatus}
            </span>
            {payment.gateway && <span className="muted small"> · via {payment.gateway}</span>}
          </span>
        </div>
      )}

      {enRoute && (
        <div className="card col motion-line">
          <div className="spread">
            <strong>Live tracking</strong>
            <button
              className={tracking ? 'ghost' : ''}
              onClick={() => (tracking ? stopTracking() : startTracking())}
            >
              {tracking ? 'Stop sharing' : 'Share my live location'}
            </button>
          </div>
          {liveEta ? (
            <>
              <div className="muted small">Estimated arrival window</div>
              <div className="eta-metric"><div className="value">
                {clockTime(liveEta.etaEarliest)} – {clockTime(liveEta.etaLatest)}
              </div></div>
              <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                <span className="badge info">{liveEta.travelMins} min travel</span>
                <span className="badge">+{liveEta.trafficBufferMins} min traffic buffer</span>
                <span className="badge ok">ready at {clockTime(liveEta.readyAt)}</span>
              </div>
              <span className="muted small">
                The store syncs prep to your live position so your order is fresh when you arrive.
              </span>
            </>
          ) : (
            <span className="muted small">
              {tracking
                ? 'Waiting for your location…'
                : 'Share your location to get an Uber-style arrival window that updates as you move.'}
            </span>
          )}
          {trackError && <span className="error small">{trackError}</span>}
        </div>
      )}

      <div className="card col">
        <div className="muted small">Pickup time</div>
        <div className="eta-metric"><div className="value">
          {new Date(order.pickupTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </div></div>
        {order.etaSegment && <span className="muted small">{order.etaSegment}</span>}

        {!cancelled && (
          <div className="timeline" style={{ marginTop: 14 }}>
            {STEPS.map((step, i) => (
              <div key={step} style={{ display: 'contents' }}>
                <div className={`dot ${i <= activeIndex ? 'on' : ''}`} title={step} />
                {i < STEPS.length - 1 && <div className={`seg ${i < activeIndex ? 'on' : ''}`} />}
              </div>
            ))}
          </div>
        )}
        {!cancelled && (
          <div className="row spread small muted">
            {STEPS.map((s) => <span key={s}>{s}</span>)}
          </div>
        )}
      </div>

      <div className="card col">
        <strong>Items</strong>
        {order.items.map((it) => (
          <div className="spread" key={it.orderItemId}>
            <span>{it.quantity} × item #{it.menuItemId}</span>
            <span className="price">₹{it.totalPrice.toFixed(0)}</span>
          </div>
        ))}
        <hr />
        <div className="spread"><strong>Total</strong><strong className="price">₹{order.totalAmount.toFixed(0)}</strong></div>
      </div>

      <p className="muted small">This page updates automatically as the store prepares your order.</p>
    </div>
  );
}
