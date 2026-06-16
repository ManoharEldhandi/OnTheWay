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

  return (
    <div className="col">
      <div className="spread">
        <div>
          <h1 className="title">Menu</h1>
          <p className="sub">Add items, then check out with a live ETA.</p>
        </div>
        <button className="ghost" onClick={() => navigate('/')}>← Back to discovery</button>
      </div>

      {error && <div className="error">{error}</div>}
      {loading && <div className="muted">Loading menu…</div>}

      <div className="grid cards">
        {items.map((item) => {
          const inCart = cart.lines.find((l) => l.item.menuItemId === item.menuItemId)?.quantity ?? 0;
          return (
            <div key={item.menuItemId} className="card col">
              <div className="spread">
                <strong>{item.name}</strong>
                <span className="price">₹{item.price.toFixed(0)}</span>
              </div>
              {item.description && <span className="muted small">{item.description}</span>}
              {!item.availability && <span className="badge warn">Unavailable</span>}
              <div className="spread">
                {inCart > 0 ? (
                  <div className="row">
                    <button onClick={() => cart.remove(item.menuItemId)}>−</button>
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
        <div className="card spread" style={{ position: 'sticky', bottom: 16 }}>
          <span><strong>{cartCount}</strong> item(s) · <span className="price">₹{cart.total.toFixed(0)}</span></span>
          <button className="success" onClick={() => navigate('/checkout')}>Go to checkout →</button>
        </div>
      )}
    </div>
  );
}
