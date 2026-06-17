import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import type { Shop } from '../../types';

const STATUS_BADGE: Record<string, string> = {
  APPROVED: 'ok', PENDING: 'warn', SUSPENDED: 'warn', REJECTED: 'warn',
};

export function MerchantShopsPage() {
  const navigate = useNavigate();
  const [shops, setShops] = useState<Shop[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      setShops(await api.get<Shop[]>('/api/merchant/shops'));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load your shops');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  return (
    <div className="col">
      <div className="spread">
        <div>
          <h1 className="title">My shops</h1>
          <p className="sub">Manage your storefronts, menus, and stock.</p>
        </div>
        <button className="primary" onClick={() => navigate('/merchant/apply')}>Open a shop</button>
      </div>

      {error && <div className="error">{error}</div>}
      {loading && <div className="muted">Loading…</div>}
      {!loading && shops.length === 0 && (
        <div className="card col">
          <strong>You don't have any shops yet.</strong>
          <span className="muted small">Apply to open one — an administrator will review it.</span>
          <button className="primary" onClick={() => navigate('/merchant/apply')}>Open your first shop</button>
        </div>
      )}

      <div className="grid cards">
        {shops.map((s) => (
          <div key={s.merchantId} className="card col">
            <div className="spread">
              <strong>{s.storeName}</strong>
              <span className={`badge ${STATUS_BADGE[s.status] ?? ''}`}>{s.status}</span>
            </div>
            <span className="badge steel">{s.storeType.toLowerCase().replace('_', ' ')}</span>
            <span className="muted small">{s.address}</span>
            {s.status === 'REJECTED' && s.statusReason && (
              <span className="error small">Rejected: {s.statusReason}</span>
            )}
            {s.status === 'SUSPENDED' && s.statusReason && (
              <span className="muted small">Suspended: {s.statusReason}</span>
            )}
            {s.status === 'PENDING' && (
              <span className="muted small">Awaiting administrator approval. You can prepare the menu now.</span>
            )}
            <Link className="btn" to={`/merchant/shops/${s.merchantId}/menu`}>Manage menu</Link>
          </div>
        ))}
      </div>
    </div>
  );
}
