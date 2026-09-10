import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { OrderChat } from '../../components/OrderChat';
import { useOrderRealtime } from '../../realtime';
import type { OrderResponse, OrderStatus } from '../../types';

// The next legal status in the lifecycle (mirrors the backend state machine).
const NEXT: Partial<Record<OrderStatus, OrderStatus>> = {
  ACCEPTED: 'PREPARING',
  PREPARING: 'READY',
};

function actionLabel(status: OrderStatus): string {
  if (status === 'PREPARING') return 'Start preparing';
  return 'Mark ready';
}

function customerArrivalStatus(etaSegment: string | null): string | null {
  const match = /travel\s+(\d+)\s+min/i.exec(etaSegment ?? '');
  if (!match) return null;
  const minutes = Number(match[1]);
  if (minutes === 0) return 'Customer has arrived · pickup may be waiting';
  if (minutes <= 5) return `Customer approaching · about ${minutes} min away`;
  return `Customer en route · about ${minutes} min away`;
}

export function MerchantOrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [pickupCodes, setPickupCodes] = useState<Record<number, string>>({});
  const [chatOrderId, setChatOrderId] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api.get<OrderResponse[]>('/api/merchant/orders');
      setOrders(data.sort((a, b) => b.orderId - a.orderId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load orders');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useOrderRealtime(useCallback(() => {
    void load();
  }, [load]));

  async function advance(order: OrderResponse, status: OrderStatus) {
    setBusyId(order.orderId);
    setError(null);
    try {
      await api.put(`/api/orders/${order.orderId}/status?status=${status}`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update status');
    } finally {
      setBusyId(null);
    }
  }

  async function acceptAndStart(order: OrderResponse) {
    setBusyId(order.orderId);
    setError(null);
    try {
      await api.post(`/api/orders/${order.orderId}/accept`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not accept and start preparation');
    } finally {
      setBusyId(null);
    }
  }

  async function confirmPickup(order: OrderResponse) {
    setBusyId(order.orderId);
    setError(null);
    try {
      await api.post(`/api/orders/${order.orderId}/pickup`, { pickupCode: pickupCodes[order.orderId] ?? '' });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not confirm pickup');
    } finally {
      setBusyId(null);
    }
  }

  const active = orders.filter((o) => o.status !== 'PICKED' && o.status !== 'CANCELLED');
  const done = orders.filter((o) => o.status === 'PICKED' || o.status === 'CANCELLED');
  const preparing = orders.filter((o) => o.status === 'PREPARING').length;
  const ready = orders.filter((o) => o.status === 'READY').length;
  const chatOrder = active.find((order) => order.orderId === chatOrderId);

  return (
    <div className="col">
      <section className="page-head">
        <div className="hero-block motion-line">
          <span className="kicker">Merchant queue / live status</span>
          <h1 className="title">Move orders through.</h1>
          <p className="sub">Accept first, then synchronize preparation and pickup timing from one focused operations queue.</p>
        </div>
        <div className="hero-panel">
          <span className="kicker">Active orders</span>
          <div className="value">{active.length}</div>
          <span className="badge info">auto refresh</span>
        </div>
      </section>

      <div className="metric-strip">
        <div className="metric-tile"><span className="kicker">Preparing</span><div className="num">{preparing}</div></div>
        <div className="metric-tile"><span className="kicker">Ready</span><div className="num">{ready}</div></div>
        <div className="metric-tile"><span className="kicker">Closed</span><div className="num">{done.length}</div></div>
      </div>
      {error && <div className="error">{error}</div>}

      <h3 className="section-title">Active ({active.length})</h3>
      <div className="grid cards">
        {active.map((o) => {
          const next = NEXT[o.status];
          const paid = o.payment?.paymentStatus === 'COMPLETED';
          const paymentStatus = o.payment?.paymentStatus ?? 'UNPAID';
          const accepted = o.status !== 'PLACED';
          const chatOpen = o.status === 'ACCEPTED' || o.status === 'PREPARING' || o.status === 'READY';
          const arrivalStatus = customerArrivalStatus(o.etaSegment);
          return (
            <div key={o.orderId} className="card col selectable">
              <div className="spread">
                <strong>Order #{o.orderId}</strong>
                <span className={`status ${o.status}`}>{o.status}</span>
              </div>
              <span className="muted small">{accepted
                ? `Customer ETA · ${new Date(o.pickupTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                : 'Accept to start preparation timing'}</span>
              <div className="row wrap">
                <span className={`badge ${paid ? 'ok' : 'warn'}`}>Payment · {paymentStatus}</span>
                {!paid && <span className="muted small">Acceptance is locked until payment clears.</span>}
              </div>
              {accepted && o.etaSegment && <span className="muted small">{o.etaSegment}</span>}
              {accepted && arrivalStatus && <span className="merchant-arrival-status">{arrivalStatus}</span>}
              {accepted && <span className="merchant-privacy-note">Timing only · customer route and coordinates are private.</span>}
              <div className="col">
                {o.items.map((it) => (
                  <span key={it.orderItemId} className="small">{it.quantity} × {it.itemName}</span>
                ))}
              </div>
              <div className="row">
                {o.status === 'PLACED' && (
                  <button className="success grow" disabled={busyId === o.orderId || !paid} onClick={() => acceptAndStart(o)}>
                    {busyId === o.orderId ? 'Starting…' : 'Accept & begin preparation'}
                  </button>
                )}
                {next && (
                  <button className="success grow" disabled={busyId === o.orderId || !paid} onClick={() => advance(o, next)}>
                    {actionLabel(next)}
                  </button>
                )}
                {chatOpen && <button className="ghost" onClick={() => setChatOrderId((id) => id === o.orderId ? null : o.orderId)}>
                  {chatOrderId === o.orderId ? 'Close chat' : 'Chat'}
                </button>}
                {(o.status === 'PLACED' || o.status === 'ACCEPTED' || o.status === 'PREPARING') && (
                  <button className="ghost" disabled={busyId === o.orderId} onClick={() => advance(o, 'CANCELLED')}>
                    Cancel
                  </button>
                )}
              </div>
              {o.status === 'READY' && (
                <div className="pickup-confirm">
                  <label>Customer pickup code</label>
                  <div className="row">
                    <input aria-label={`Pickup code for order ${o.orderId}`} value={pickupCodes[o.orderId] ?? ''}
                      onChange={(event) => setPickupCodes((codes) => ({ ...codes, [o.orderId]: event.target.value }))}
                      placeholder="e.g. 123456" maxLength={12} />
                    <button className="primary" disabled={busyId === o.orderId} onClick={() => confirmPickup(o)}>Confirm pickup</button>
                  </div>
                </div>
              )}
            </div>
          );
        })}
        {active.length === 0 && <div className="card muted">No active orders right now.</div>}
      </div>

      {chatOrder && (
        <OrderChat orderId={chatOrder.orderId} active compact />
      )}

      {done.length > 0 && (
        <>
          <h3 className="section-title">Completed</h3>
          <div className="grid cards">
            {done.map((o) => (
              <div key={o.orderId} className="card spread">
                <strong>Order #{o.orderId}</strong>
                <span className={`status ${o.status}`}>{o.status}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
