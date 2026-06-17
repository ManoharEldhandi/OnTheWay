import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { useOrderRealtime } from '../../realtime';
import type { OrderResponse, OrderStatus } from '../../types';

// The next legal status in the lifecycle (mirrors the backend state machine).
const NEXT: Partial<Record<OrderStatus, OrderStatus>> = {
  PLACED: 'PREPARING',
  PREPARING: 'READY',
  READY: 'PICKED',
};

export function MerchantOrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

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

  const active = orders.filter((o) => o.status !== 'PICKED' && o.status !== 'CANCELLED');
  const done = orders.filter((o) => o.status === 'PICKED' || o.status === 'CANCELLED');
  const preparing = orders.filter((o) => o.status === 'PREPARING').length;
  const ready = orders.filter((o) => o.status === 'READY').length;

  return (
    <div className="col">
      <section className="page-head">
        <div className="hero-block motion-line">
          <span className="kicker">Merchant queue / live status</span>
          <h1 className="title">Move orders through.</h1>
          <p className="sub">Advance every shop's order state from one focused operations queue.</p>
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
          return (
            <div key={o.orderId} className="card col selectable">
              <div className="spread">
                <strong>Order #{o.orderId}</strong>
                <span className={`status ${o.status}`}>{o.status}</span>
              </div>
              <span className="muted small">
                Pickup {new Date(o.pickupTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
              {o.etaSegment && <span className="muted small">{o.etaSegment}</span>}
              <div className="col">
                {o.items.map((it) => (
                  <span key={it.orderItemId} className="small">{it.quantity} × item #{it.menuItemId}</span>
                ))}
              </div>
              <div className="row">
                {next && (
                  <button className="success grow" disabled={busyId === o.orderId} onClick={() => advance(o, next)}>
                    Mark {next}
                  </button>
                )}
                {(o.status === 'PLACED' || o.status === 'PREPARING') && (
                  <button className="ghost" disabled={busyId === o.orderId} onClick={() => advance(o, 'CANCELLED')}>
                    Cancel
                  </button>
                )}
              </div>
            </div>
          );
        })}
        {active.length === 0 && <div className="card muted">No active orders right now.</div>}
      </div>

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
