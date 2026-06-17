import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useCart } from '../cart/CartContext';
import { getLocation } from '../location';
import type { EtaQuote, OrderResponse } from '../types';

function minutesUntil(iso: string): number {
  return Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 60000));
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();
  const [quote, setQuote] = useState<EtaQuote | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);
  const coords = getLocation();

  useEffect(() => {
    if (cart.merchantId == null) return;
    (async () => {
      try {
        const q = await api.post<EtaQuote>('/api/eta/quote', {
          merchantId: cart.merchantId,
          latitude: coords.latitude,
          longitude: coords.longitude,
        });
        setQuote(q);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not compute ETA');
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cart.merchantId]);

  async function placeOrder() {
    if (cart.merchantId == null) return;
    setPlacing(true);
    setError(null);
    try {
      const order = await api.post<OrderResponse>('/api/orders', {
        merchantId: cart.merchantId,
        latitude: coords.latitude,
        longitude: coords.longitude,
        paymentMethod: 'CARD',
        items: cart.lines.map((l) => ({ menuItemId: l.item.menuItemId, quantity: l.quantity })),
      });
      // Pay for the order through the gateway (mock by default).
      try {
        await api.post('/api/payments', { orderId: order.orderId, paymentMethod: 'CARD' });
      } catch {
        // Payment failure shouldn't lose the order; the order page shows payment status.
      }
      cart.clear();
      navigate(`/orders/${order.orderId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not place order');
    } finally {
      setPlacing(false);
    }
  }

  if (cart.lines.length === 0) {
    return (
      <div className="col">
        <h1 className="title">Your cart is empty</h1>
        <button className="primary" onClick={() => navigate('/')}>Find stores</button>
      </div>
    );
  }

  return (
    <div className="col">
      <section className="page-head compact">
        <div className="hero-block motion-line">
          <span className="kicker">Checkout / route sync</span>
          <h1 className="title">Lock the pickup window.</h1>
          <p className="sub">The order is timed from your current location so the shop starts at the right minute.</p>
        </div>
      </section>

      {quote && (
        <div className="card pad-lg motion-line">
          <span className="badge ok">ETA-synced pickup</span>
          <div className="eta-grid" style={{ marginTop: 12 }}>
            <div className="eta-metric">
              <div className="label">Ready in</div>
              <div className="value">{minutesUntil(quote.readyAt)} min</div>
              <div className="meta">at {new Date(quote.readyAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
            </div>
            <div className="eta-metric">
              <div className="label">The store starts preparing</div>
              <div className="value">{minutesUntil(quote.prepStartAt) === 0 ? 'now' : `in ${minutesUntil(quote.prepStartAt)} min`}</div>
              <div className="meta">so it's fresh on arrival</div>
            </div>
            <div className="eta-metric">
              <div className="label">Arrival window</div>
              <div className="value" style={{ fontSize: '1.1rem' }}>
                {new Date(quote.etaEarliest).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                {' – '}
                {new Date(quote.etaLatest).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </div>
              <div className="meta">±{quote.trafficBufferMins} min traffic buffer</div>
            </div>
          </div>
          <div className="timeline">
            <div className="dot on" />
            <div className="seg on" />
            <span className="badge info">{quote.travelMins} min travel</span>
            <div className="seg on" />
            <span className="badge warn">{quote.prepTimeMins}+{quote.bufferMins} min prep</span>
            <div className="seg on" />
            <div className="dot on" />
          </div>
          <p className="muted small" style={{ marginTop: 8 }}>
            {quote.distanceKm} km away · the kitchen is timed to your arrival.
          </p>
        </div>
      )}

      <div className="card col">
        <div className="spread">
          <strong>Cart manifest</strong>
          <span className="badge steel">{cart.lines.length} line(s)</span>
        </div>
        {cart.lines.map((l) => (
          <div className="spread" key={l.item.menuItemId}>
            <span>{l.quantity} × {l.item.name}</span>
            <span className="price">₹{(l.item.price * l.quantity).toFixed(0)}</span>
          </div>
        ))}
        <hr />
        <div className="spread">
          <strong>Total</strong>
          <strong className="price">₹{cart.total.toFixed(0)}</strong>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="row">
        <button className="ghost" onClick={() => navigate(-1)}>Keep shopping</button>
        <button className="success grow" disabled={placing} onClick={placeOrder}>
          {placing ? 'Placing order…' : 'Place order'}
        </button>
      </div>
    </div>
  );
}
