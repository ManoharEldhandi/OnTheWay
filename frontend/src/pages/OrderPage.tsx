import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { OrderResponse, OrderStatus } from '../types';

interface PaymentInfo {
  paymentStatus: string;
  gateway: string | null;
  amount: number;
}

const STEPS: OrderStatus[] = ['PLACED', 'PREPARING', 'READY', 'PICKED'];

export function OrderPage() {
  const { orderId } = useParams();
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [payment, setPayment] = useState<PaymentInfo | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
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
  }

  useEffect(() => {
    void load();
    // Poll so the customer sees live status changes from the merchant.
    const t = setInterval(load, 4000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  if (error) return <div className="error">{error}</div>;
  if (!order) return <div className="muted">Loading…</div>;

  const activeIndex = STEPS.indexOf(order.status);
  const cancelled = order.status === 'CANCELLED';

  return (
    <div className="col">
      <div className="spread">
        <h1 className="title">Order #{order.orderId}</h1>
        <span className={`status ${order.status}`}>{order.status}</span>
      </div>

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
