import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useCart } from '../cart/CartContext';
import type { MenuItem } from '../types';

export function StorePage() {
  const { merchantId } = useParams();
  const navigate = useNavigate();
  const cart = useCart();
  const [items, setItems] = useState<MenuItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const data = await api.get<MenuItem[]>(`/api/menu-items/merchant/${merchantId}`);
        setItems(data);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load menu');
      } finally {
        setLoading(false);
      }
    })();
  }, [merchantId]);

  const cartCount = cart.lines.reduce((n, l) => n + l.quantity, 0);
  const available = items.filter((item) => item.availability).length;

  return (
    <div className="col">
      <section className="page-head compact">
        <div className="hero-block">
          <span className="kicker">Shop menu / ETA cart</span>
          <h1 className="title">Build the pickup.</h1>
          <p className="sub">Choose available items and check out against a live arrival window.</p>
        </div>
        <button className="ghost" onClick={() => navigate('/')}>Back to discovery</button>
      </section>

      <div className="metric-strip">
        <div className="metric-tile"><span className="kicker">Items</span><div className="num">{items.length}</div></div>
        <div className="metric-tile"><span className="kicker">Available</span><div className="num">{available}</div></div>
        <div className="metric-tile"><span className="kicker">Cart</span><div className="num">{cartCount}</div></div>
      </div>

      {error && <div className="error">{error}</div>}
      {loading && <div className="card muted mono">Loading menu...</div>}

      <div className="grid cards">
        {items.map((item) => {
          const inCart = cart.lines.find((l) => l.item.menuItemId === item.menuItemId)?.quantity ?? 0;
          return (
            <div key={item.menuItemId} className="card col selectable">
              <div className="spread">
                <strong>{item.name}</strong>
                <span className="price">₹{item.price.toFixed(0)}</span>
              </div>
              {item.description && <span className="muted small">{item.description}</span>}
              {!item.availability && <span className="badge warn">Unavailable</span>}
              <div className="spread">
                {inCart > 0 ? (
                  <div className="row">
                    <button onClick={() => cart.remove(item.menuItemId)}>-</button>
                    <strong>{inCart}</strong>
                    <button onClick={() => cart.add(item)} disabled={!item.availability}>+</button>
                  </div>
                ) : (
                  <button className="primary" disabled={!item.availability} onClick={() => cart.add(item)}>
                    Add to cart
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {cartCount > 0 && (
        <div className="card spread sticky-bar">
          <span><strong>{cartCount}</strong> item(s) · <span className="price">₹{cart.total.toFixed(0)}</span></span>
          <button className="success" onClick={() => navigate('/checkout')}>Go to checkout</button>
        </div>
      )}
    </div>
  );
}
