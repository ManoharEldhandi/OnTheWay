import { useNavigate, useSearchParams } from 'react-router-dom';

/** A public landing surface encoded by the checkout's harmless QR payment code. */
export function DemoPaymentPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const amount = params.get('amount');

  return (
    <main className="center demo-payment-page">
      <section className="card col demo-payment-card motion-line">
        <div className="row"><span className="login-mark" /><span className="brand-name">OnTheWay</span></div>
        <span className="badge ok">Demo QR payment</span>
        <div>
          <h1>Payment confirmed</h1>
          <p>This is a safe simulated confirmation for the OnTheWay pickup flow{amount ? ` · ₹${amount}` : ''}.</p>
        </div>
        <div className="demo-payment-check" aria-hidden="true">✓</div>
        <p className="muted small">No real payment was initiated. Return to the checkout tab to place the test order.</p>
        <button className="primary" onClick={() => navigate('/login')}>Open OnTheWay</button>
      </section>
    </main>
  );
}
