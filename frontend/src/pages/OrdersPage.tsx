import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { OrderResponse } from '../types';

export function OrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const data = await api.get<OrderResponse[]>('/api/orders/user');
        setOrders(data.sort((a, b) => b.orderId - a.orderId));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load orders');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <div className="col">
      <h1 className="title">My orders</h1>
      {error && <div className="error">{error}</div>}
      {loading && <div className="muted">Loading…</div>}
      {!loading && orders.length === 0 && <div className="muted">No orders yet.</div>}
      <div className="grid cards">
        {orders.map((o) => (
          <Link key={o.orderId} to={`/orders/${o.orderId}`} className="card col" style={{ color: 'inherit' }}>
            <div className="spread">
              <strong>Order #{o.orderId}</strong>
              <span className={`status ${o.status}`}>{o.status}</span>
            </div>
            <span className="muted small">{o.items.length} item(s) · ₹{o.totalAmount.toFixed(0)}</span>
            <span className="muted small">
              Pickup {new Date(o.pickupTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}
