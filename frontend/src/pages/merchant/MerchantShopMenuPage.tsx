import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import type { MenuItem } from '../../types';

export function MerchantShopMenuPage() {
  const { shopId } = useParams();
  const navigate = useNavigate();
  const [items, setItems] = useState<MenuItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // New-item form state.
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');

  async function load() {
    try {
      setItems(await api.get<MenuItem[]>(`/api/menu-items/merchant/${shopId}`));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load the menu');
    }
  }

  useEffect(() => { void load(); }, [shopId]);

  async function addItem(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.post(`/api/menu-items/${shopId}`, {
        name, description: description || null, price: Number(price), availability: true,
      });
      setName(''); setDescription(''); setPrice('');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add the item');
    } finally {
      setBusy(false);
    }
  }

  async function updateItem(item: MenuItem, changes: Partial<MenuItem>) {
    setError(null);
    try {
      await api.put(`/api/menu-items/${item.menuItemId}`, {
        price: changes.price ?? item.price,
        availability: changes.availability ?? item.availability,
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update the item');
    }
  }

  async function removeItem(item: MenuItem) {
    setError(null);
    try {
      await api.del(`/api/menu-items/${item.menuItemId}`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete the item');
    }
  }

  return (
    <div className="col">
      <div className="spread">
        <div>
          <h1 className="title">Menu</h1>
          <p className="sub">Add items, change prices, and toggle availability.</p>
        </div>
        <button className="ghost" onClick={() => navigate('/merchant')}>Back to my shops</button>
      </div>

      {error && <div className="error">{error}</div>}

      <form className="card filters-grid" onSubmit={addItem}>
        <div className="col">
          <label>Item name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="col">
          <label>Price</label>
          <input type="number" min={0} step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} required />
        </div>
        <div className="col" style={{ gridColumn: '1 / -1' }}>
          <label>Description (optional)</label>
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <button className="primary" type="submit" disabled={busy} style={{ gridColumn: '1 / -1' }}>
          {busy ? 'Adding…' : 'Add item'}
        </button>
      </form>

      <div className="grid cards">
        {items.map((item) => (
          <div key={item.menuItemId} className="card col">
            <div className="spread">
              <strong>{item.name}</strong>
              <span className={`badge ${item.availability ? 'ok' : 'warn'}`}>
                {item.availability ? 'In stock' : 'Out of stock'}
              </span>
            </div>
            {item.description && <span className="muted small">{item.description}</span>}
            <div className="row">
              <label className="small">Price ₹</label>
              <input
                type="number" min={0} step="0.01" defaultValue={item.price}
                onBlur={(e) => {
                  const v = Number(e.target.value);
                  if (v !== item.price) void updateItem(item, { price: v });
                }}
                style={{ maxWidth: 120 }}
              />
            </div>
            <div className="row">
              <button onClick={() => updateItem(item, { availability: !item.availability })}>
                {item.availability ? 'Mark out of stock' : 'Mark in stock'}
              </button>
              <button className="ghost" onClick={() => removeItem(item)}>Delete</button>
            </div>
          </div>
        ))}
        {items.length === 0 && <div className="muted">No items yet. Add your first item above.</div>}
      </div>
    </div>
  );
}
