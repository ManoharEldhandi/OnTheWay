import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { EtaExplainer } from '../components/EtaExplainer';
import { JourneyMap } from '../components/JourneyMap';
import { OrderChat } from '../components/OrderChat';
import { DEMO_ROUTE, type DemoRouteStop } from '../location';
import { useOrderRealtime } from '../realtime';
import type { DemoStatus, EtaQuote, OrderResponse, OrderStatus, PaymentResponse } from '../types';

const STEPS: OrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'PICKED'];
// Real routes are expressed in minutes, while the walkthrough gives the same
// complete route a calm thirty-second presentation pace for conversation and hand-offs.
const ACCELERATED_JOURNEY_MS = 30_000;

function isEnRoute(status: OrderStatus): boolean {
  return status === 'ACCEPTED' || status === 'PREPARING';
}

function statusLabel(status: OrderStatus): string {
  return status === 'PLACED' ? 'Awaiting acceptance' : status.replace('_', ' ');
}

function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function paymentTone(status: PaymentResponse['paymentStatus']): string {
  if (status === 'COMPLETED') return 'ok';
  if (status === 'FAILED' || status === 'REFUNDED') return 'warn';
  return 'info';
}

/**
 * The demo sends the very same checkpoints to the ETA endpoint that it draws on
 * screen. Its final stop is the order's shop, rather than a hard-coded landmark,
 * so the walkthrough remains truthful after a customer chooses a different shop.
 */
function routeForOrder(order: OrderResponse): DemoRouteStop[] {
  if (order.merchantLatitude == null || order.merchantLongitude == null) return DEMO_ROUTE;

  const start = order.customerLatitude != null && order.customerLongitude != null
    ? {
        label: 'Your starting point',
        detail: 'route begins here',
        coords: { latitude: order.customerLatitude, longitude: order.customerLongitude },
      }
    : DEMO_ROUTE[0];
  const destination = { latitude: order.merchantLatitude, longitude: order.merchantLongitude };
  const waypoint = (progress: number, bend: number) => {
    const latitudeDelta = destination.latitude - start.coords.latitude;
    const longitudeDelta = destination.longitude - start.coords.longitude;
    const length = Math.max(Math.hypot(latitudeDelta, longitudeDelta), 0.0001);
    return {
      latitude: Number((start.coords.latitude + latitudeDelta * progress + (longitudeDelta / length) * bend).toFixed(5)),
      longitude: Number((start.coords.longitude + longitudeDelta * progress - (latitudeDelta / length) * bend).toFixed(5)),
    };
  };
  return [
    start,
    { label: 'Route checkpoint', detail: 'route update', coords: waypoint(.24, .006) },
    { label: 'Traffic recheck', detail: 'traffic recheck', coords: waypoint(.49, -.004) },
    { label: 'City connector', detail: 'arrival update', coords: waypoint(.74, .003) },
    { label: order.merchantName ?? 'Pickup counter', detail: 'pickup counter', coords: destination },
  ];
}

export function OrderPage() {
  const { orderId } = useParams();
  const location = useLocation();
  const notice = (location.state as { justPaid?: boolean; paymentError?: string; paymentPending?: boolean } | null) ?? null;
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [liveEta, setLiveEta] = useState<EtaQuote | null>(null);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [demoMode, setDemoMode] = useState(false);
  const [demoRouteIndex, setDemoRouteIndex] = useState(0);
  const [demoRunning, setDemoRunning] = useState(false);
  const [journeyProgress, setJourneyProgress] = useState(0);
  const [activeRoute, setActiveRoute] = useState<DemoRouteStop[] | null>(null);
  const demoFrame = useRef<number | null>(null);
  const journeySequence = useRef(0);
  const lastCheckpoint = useRef(-1);
  const autoStartedOrder = useRef<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api.get<OrderResponse>(`/api/orders/${orderId}`);
      setOrder(data);
      if (data.payment) {
        setPayment(data.payment);
      } else {
        try {
          setPayment(await api.get<PaymentResponse>(`/api/payments/order/${orderId}`));
        } catch {
          setPayment(null);
        }
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load order');
    }
  }, [orderId]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    api.get<DemoStatus>('/api/demo/status')
      .then((status) => setDemoMode(status.enabled))
      .catch(() => setDemoMode(false));
  }, []);

  useOrderRealtime(useCallback((event) => {
    if (String(event.orderId) === String(orderId)) void load();
  }, [load, orderId]));

  function stopDemoJourney() {
    journeySequence.current += 1;
    if (demoFrame.current !== null) window.cancelAnimationFrame(demoFrame.current);
    demoFrame.current = null;
    setDemoRunning(false);
  }

  async function simulateDemoStop(index: number, route: readonly DemoRouteStop[], sequence: number) {
    const safeIndex = Math.min(index, route.length - 1);
    const stop = route[safeIndex];
    setDemoRouteIndex(safeIndex);
    try {
      const eta = await api.post<EtaQuote>(`/api/orders/${orderId}/location`, stop.coords);
      if (sequence !== journeySequence.current) return;
      setLiveEta(eta);
      await load();
      if (sequence !== journeySequence.current) return;
    } catch (err) {
      if (sequence !== journeySequence.current) return;
      setError(err instanceof ApiError ? err.message : 'Could not simulate the demo route');
      stopDemoJourney();
    }
  }

  function runDemoJourney(route: readonly DemoRouteStop[], synchronizeEta: boolean) {
    stopDemoJourney();
    const sequence = journeySequence.current;
    lastCheckpoint.current = -1;
    setActiveRoute([...route]);
    setDemoRouteIndex(0);
    setJourneyProgress(0);
    setDemoRunning(true);
    const startedAt = performance.now();

    const advance = (now: number) => {
      const progress = Math.min((now - startedAt) / ACCELERATED_JOURNEY_MS, 1);
      setJourneyProgress(progress);
      const checkpoint = Math.min(
        Math.floor(progress * (route.length - 1) + Number.EPSILON),
        route.length - 1,
      );
      if (checkpoint > lastCheckpoint.current) {
        lastCheckpoint.current = checkpoint;
        setDemoRouteIndex(checkpoint);
        if (synchronizeEta) void simulateDemoStop(checkpoint, route, sequence);
      }
      if (progress < 1 && sequence === journeySequence.current) {
        demoFrame.current = window.requestAnimationFrame(advance);
      } else if (sequence === journeySequence.current) {
        demoFrame.current = null;
        setDemoRunning(false);
      }
    };
    demoFrame.current = window.requestAnimationFrame(advance);
  }

  async function retryPayment() {
    setPaymentBusy(true);
    try {
      const result = await api.post<PaymentResponse>('/api/payments', {
        orderId: Number(orderId), paymentMethod: 'DEMO_UPI',
      });
      setPayment(result);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not retry payment');
    } finally {
      setPaymentBusy(false);
    }
  }

  async function cancelOrder() {
    setCancelling(true);
    try {
      await api.post(`/api/orders/${orderId}/cancel`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel order');
    } finally {
      setCancelling(false);
    }
  }

  useEffect(() => {
    if (order && !isEnRoute(order.status) && demoRunning) stopDemoJourney();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order?.status]);

  useEffect(() => () => {
    if (demoFrame.current !== null) window.cancelAnimationFrame(demoFrame.current);
  }, []);

  const orderRoute = order ? routeForOrder(order) : DEMO_ROUTE;

  useEffect(() => {
    if (!order || !isEnRoute(order.status)) {
      autoStartedOrder.current = null;
      return;
    }
    // In the local product profile checkpoints also update the real ETA service. Elsewhere the
    // same smooth animation remains an arrival projection until the customer shares location.
    const journeyKey = `${order.orderId}:${demoMode ? 'synchronized' : 'projected'}`;
    if (autoStartedOrder.current === journeyKey) return;
    autoStartedOrder.current = journeyKey;
    const timer = window.setTimeout(() => {
      runDemoJourney(orderRoute, demoMode && order.merchantLatitude != null && order.merchantLongitude != null);
    }, 180);
    return () => window.clearTimeout(timer);
    // The route starts from the accepted order snapshot; it must not restart on ETA refreshes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order?.orderId, order?.status, demoMode]);

  if (error && !order) return <div className="error">{error}</div>;
  if (!order) return <div className="muted">Loading order…</div>;

  const activeIndex = STEPS.indexOf(order.status);
  const cancelled = order.status === 'CANCELLED';
  const enRoute = isEnRoute(order.status);
  const canCancel = order.status === 'PLACED';
  const demoRoute = orderRoute;
  const displayedRoute = activeRoute ?? demoRoute;
  const demoStop = displayedRoute[Math.min(demoRouteIndex, displayedRoute.length - 1)];

  return (
    <div className="col order-page">
      {enRoute ? (
        <section className="tracking-page-head">
          <div>
            <span className="kicker">Order #{order.orderId} · live pickup</span>
            <h1>{order.status === 'PREPARING' ? 'Your order is being prepared.' : 'The shop has accepted your order.'}</h1>
            <p>{order.merchantName ?? 'Your pickup counter'} sees your arrival window, while this route remains visible only to you.</p>
          </div>
          <div className="tracking-state"><span className={`status ${order.status}`}>{statusLabel(order.status)}</span><strong>{demoRunning ? 'Route moving' : 'Route ready'}</strong></div>
        </section>
      ) : (
        <section className="page-head">
          <div className="hero-block">
            <span className="kicker">Order #{order.orderId} · live pickup</span>
            <h1 className="title">Your pickup, in sync.</h1>
            <p className="sub">Payment, preparation, route timing, and hand-off stay together.</p>
          </div>
          <div className="hero-panel">
            <span className="kicker">Current state</span>
            <div className="value">{statusLabel(order.status)}</div>
            <span className={`status ${order.status}`}>{statusLabel(order.status)}</span>
          </div>
        </section>
      )}

      {notice?.justPaid && <div className="success-note">Payment confirmed. The shop can now begin at the scheduled time.</div>}
      {notice?.paymentPending && <div className="success-note">Payment submitted. We will unlock preparation as soon as the provider confirms it.</div>}
      {notice?.paymentError && <div className="error">{notice.paymentError}. Your order is saved; choose a retry below.</div>}
      {error && <div className="error">{error}</div>}

      <section className="card payment-summary">
        <div>
          <span className="kicker">Payment status</span>
          {payment ? <strong>{payment.paymentMethod.replace('DEMO_', 'Demo ')}</strong> : <strong>Payment required</strong>}
        </div>
        {payment ? (
          <div className="payment-result">
            <span className={`badge ${paymentTone(payment.paymentStatus)}`}>{payment.paymentStatus}</span>
            <span className="muted small">{payment.attemptCount} attempt{payment.attemptCount === 1 ? '' : 's'}{payment.gateway ? ` · ${payment.gateway}` : ''}</span>
            {payment.paymentStatus === 'FAILED' && <button className="primary" disabled={paymentBusy} onClick={retryPayment}>
              {paymentBusy ? 'Retrying…' : 'Retry with Demo UPI'}
            </button>}
          </div>
        ) : <span className="badge warn">Awaiting payment</span>}
        {payment?.failureReason && <p className="muted small payment-reason">{payment.failureReason}</p>}
      </section>

      {order.status === 'PLACED' && (
        <section className="acceptance-wait card">
          <div className="acceptance-icon" aria-hidden="true">⌁</div>
          <div>
            <span className="kicker">Shop review</span>
            <strong>{order.merchantName ?? 'The shop'} is reviewing your pickup request.</strong>
            <p className="muted small">Route tracking, preparation timing, and order chat unlock as soon as the shop accepts.</p>
          </div>
        </section>
      )}

      {enRoute && (
        <section className="tracking-stage">
          <JourneyMap route={displayedRoute} routeIndex={demoRouteIndex} progress={journeyProgress} isPlaying={demoRunning} />
          <div className="tracking-map-top">
            <span className="tracking-live-dot" />
            <span>{demoRunning ? 'Live route in progress' : 'Live route ready'}</span>
            <b>{demoStop.label}</b>
          </div>
          <div className="tracking-sheet">
            <div className="tracking-sheet-copy"><span className="kicker">{order.merchantName ?? 'Pickup counter'}</span><strong>{order.status === 'PREPARING' ? 'Preparing your order now' : 'Pickup is being scheduled'}</strong></div>
            <div className="tracking-metrics">
              {liveEta ? <><span><small>Arrival window</small><b>{clockTime(liveEta.etaEarliest)} – {clockTime(liveEta.etaLatest)}</b></span><span><small>Travel time</small><b>{liveEta.travelMins} min</b></span></> : <span><small>Route status</small><b>Calculating your pickup window…</b></span>}
            </div>
          </div>
        </section>
      )}

      {order.status !== 'PICKED' && order.status !== 'CANCELLED' && (
        <OrderChat orderId={order.orderId} active={order.status === 'ACCEPTED' || order.status === 'PREPARING' || order.status === 'READY'} />
      )}

      <section className="card col">
        <div className="spread"><strong>Pickup progress</strong>{canCancel && <button className="ghost" disabled={cancelling} onClick={cancelOrder}>{cancelling ? 'Cancelling…' : 'Cancel & refund'}</button>}</div>
        <div className="muted small">Scheduled pickup {clockTime(order.pickupTime)}</div>
        {order.etaSegment && <span className="muted small">{order.etaSegment}</span>}
        {!cancelled && <>
          <div className="timeline">{STEPS.map((step, i) => <div key={step} style={{ display: 'contents' }}><div className={`dot ${i <= activeIndex ? 'on' : ''}`} title={step} />{i < STEPS.length - 1 && <div className={`seg ${i < activeIndex ? 'on' : ''}`} />}</div>)}</div>
          <div className="row spread small muted">{STEPS.map((step) => <span key={step}>{step}</span>)}</div>
        </>}
      </section>

      {enRoute && liveEta && <details className="eta-details"><summary>How the pickup time is calculated</summary><EtaExplainer eta={liveEta} live preparingNow={order.status === 'PREPARING'} /></details>}

      {order.status === 'READY' && order.pickupCode && (
        <section className="card pickup-code-card">
          <div><span className="kicker">At the counter</span><strong>Show this pickup code</strong><p className="muted small">The merchant verifies it before handing over your order.</p></div>
          <code>{order.pickupCode}</code>
        </section>
      )}

      <section className="card col">
        <strong>Order summary</strong>
        {order.items.map((item) => <div className="spread" key={item.orderItemId}><span>{item.quantity} × {item.itemName}</span><span className="price">₹{item.totalPrice.toFixed(0)}</span></div>)}
        <hr />
        <div className="spread"><strong>Total</strong><strong className="price">₹{order.totalAmount.toFixed(0)}</strong></div>
      </section>
    </div>
  );
}
