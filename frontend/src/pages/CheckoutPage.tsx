import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { api, ApiError } from '../api/client';
import { useCart } from '../cart/CartContext';
import { EtaExplainer } from '../components/EtaExplainer';
import { DEMO_ROUTE, getLocation, setLocation } from '../location';
import type { EtaQuote, OrderResponse, PaymentProviderConfig, PaymentResponse } from '../types';

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

function loadRazorpay(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Razorpay checkout.'));
    document.body.appendChild(script);
  });
}

function minutesUntil(iso: string): number {
  return Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 60000));
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();
  const [quote, setQuote] = useState<EtaQuote | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'DEMO_UPI' | 'DEMO_CARD' | 'DEMO_DECLINE'>('DEMO_UPI');
  const [provider, setProvider] = useState<PaymentProviderConfig | null>(null);
  const [coords, setCoords] = useState(getLocation);

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
  }, [cart.merchantId, coords]);

  useEffect(() => {
    api.get<PaymentProviderConfig>('/api/payments/provider')
      .then(setProvider)
      .catch(() => setProvider(null));
  }, []);

  const demoMode = provider?.demo !== false;
  const qrPaymentUrl = `${window.location.origin}/payment-demo?amount=${encodeURIComponent(cart.total.toFixed(0))}`;

  function useGuidedDemoStart() {
    const start = DEMO_ROUTE[0].coords;
    setLocation(start);
    setCoords(start);
  }

  async function placeOrder() {
    if (cart.merchantId == null) return;
    if (!demoMode && provider?.provider !== 'razorpay') {
      setError('This browser checkout supports the configured Razorpay provider. Switch PAYMENT_PROVIDER to razorpay or mock.');
      return;
    }
    setPlacing(true);
    setError(null);
    let order: OrderResponse;
    try {
      order = await api.post<OrderResponse>('/api/orders', {
        merchantId: cart.merchantId,
        latitude: coords.latitude,
        longitude: coords.longitude,
        paymentMethod: demoMode ? paymentMethod : 'RAZORPAY',
        items: cart.lines.map((l) => ({ menuItemId: l.item.menuItemId, quantity: l.quantity })),
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not place order');
      setPlacing(false);
      return;
    }

    // Orders intentionally survive a declined payment so the customer can choose another method.
    // The fulfilment gate on the server prevents an unpaid order from entering preparation.
    cart.clear();
    try {
      const payment = await api.post<PaymentResponse>('/api/payments', {
        orderId: order.orderId,
        paymentMethod: demoMode ? paymentMethod : 'RAZORPAY',
      });
      if (provider?.provider === 'razorpay' && provider.publicKey && payment.paymentStatus === 'PENDING') {
        await loadRazorpay();
        await new Promise<void>((resolve, reject) => {
          const Razorpay = window.Razorpay;
          if (!Razorpay) {
            reject(new Error('Razorpay checkout is unavailable.'));
            return;
          }
          const checkout = new Razorpay({
            key: provider.publicKey,
            order_id: payment.gatewayReference,
            amount: Math.round(payment.amount * 100),
            currency: payment.currency ?? 'INR',
            name: 'OnTheWay',
            description: `Pickup order #${order.orderId}`,
            handler: () => resolve(),
            modal: { ondismiss: () => reject(new Error('Payment was cancelled. You can retry from the order page.')) },
            theme: { color: '#2d5bd1' },
          });
          checkout.open();
        });
        navigate(`/orders/${order.orderId}`, { state: { paymentPending: true } });
        return;
      }
      navigate(`/orders/${order.orderId}`, { state: { justPaid: payment.paymentStatus === 'COMPLETED' } });
    } catch (err) {
      navigate(`/orders/${order.orderId}`, {
        state: { paymentError: err instanceof ApiError ? err.message : 'Payment could not be completed' },
      });
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
          {demoMode && <div className="guided-start">
            <div><strong>Choose an example starting point</strong><span>Whitefield gives this order a longer route. It starts moving automatically after the shop accepts.</span></div>
            <button className="ghost" onClick={useGuidedDemoStart}>Use Whitefield start</button>
          </div>}
          <EtaExplainer eta={quote} />
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

      <section className="card col payment-card">
        <div className="spread">
          <div>
            <span className="kicker">Payment</span>
            <strong className="payment-title">{demoMode ? 'Choose a demo payment' : 'Secure checkout'}</strong>
          </div>
          <span className="badge">{demoMode ? 'No real money' : provider?.provider ?? 'Loading provider'}</span>
        </div>
        {demoMode ? <div className="payment-options" role="radiogroup" aria-label="Demo payment method">
          <label className={`payment-option ${paymentMethod === 'DEMO_UPI' ? 'selected' : ''}`}>
            <input type="radio" name="payment" value="DEMO_UPI" checked={paymentMethod === 'DEMO_UPI'}
              onChange={() => setPaymentMethod('DEMO_UPI')} />
            <span><strong>Demo UPI</strong><small>Instant success · recommended walkthrough</small></span>
          </label>
          <label className={`payment-option ${paymentMethod === 'DEMO_CARD' ? 'selected' : ''}`}>
            <input type="radio" name="payment" value="DEMO_CARD" checked={paymentMethod === 'DEMO_CARD'}
              onChange={() => setPaymentMethod('DEMO_CARD')} />
            <span><strong>Demo card</strong><small>Instant success · no card details required</small></span>
          </label>
          <label className={`payment-option ${paymentMethod === 'DEMO_DECLINE' ? 'selected' : ''}`}>
            <input type="radio" name="payment" value="DEMO_DECLINE" checked={paymentMethod === 'DEMO_DECLINE'}
              onChange={() => setPaymentMethod('DEMO_DECLINE')} />
            <span><strong>Show a decline</strong><small>Safe retry scenario for the demo</small></span>
          </label>
          <div className="demo-qr-payment">
            <div>
              <span className="badge info">QR payment</span>
              <strong>Scan the OnTheWay test QR</strong>
              <p>It opens a simulated payment confirmation in OnTheWay. No money or personal data is used.</p>
              <small>For a phone scan, start in network mode so the QR contains your reachable app address.</small>
            </div>
            <div className="demo-qr-code" title="OnTheWay demo payment QR code">
              <QRCodeSVG value={qrPaymentUrl} size={112} level="M" includeMargin />
            </div>
          </div>
        </div> : <div className="payment-live-note">
          <strong>Pay by UPI, card, or netbanking with Razorpay.</strong>
          <span className="muted small">The secure provider window opens after your order total is confirmed.</span>
        </div>}
      </section>

      {error && <div className="error">{error}</div>}

      <div className="row">
        <button className="ghost" onClick={() => navigate(-1)}>Keep shopping</button>
        <button className="success grow" disabled={placing} onClick={placeOrder}>
          {placing ? 'Confirming payment…' : 'Place order & pay'}
        </button>
      </div>
    </div>
  );
}
