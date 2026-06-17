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

  const approved = shops.filter((shop) => shop.status === 'APPROVED').length;
  const pending = shops.filter((shop) => shop.status === 'PENDING').length;
  const blocked = shops.filter((shop) => shop.status === 'SUSPENDED' || shop.status === 'REJECTED').length;

  return (
    <div className="col">
      <section className="page-head">
        <div className="hero-block motion-line">
          <span className="kicker">Merchant operations / multi-shop</span>
          <h1 className="title">Run the storefronts.</h1>
          <p className="sub">Own the menu, stock, price, and applications from one operating board.</p>
        </div>
        <div className="hero-panel">
          <span className="kicker">Total shops</span>
          <div className="value">{shops.length}</div>
          <button className="primary" onClick={() => navigate('/merchant/apply')}>Open a shop</button>
        </div>
      </section>

      <div className="metric-strip">
        <div className="metric-tile"><span className="kicker">Approved</span><div className="num">{approved}</div></div>
        <div className="metric-tile"><span className="kicker">Pending</span><div className="num">{pending}</div></div>
        <div className="metric-tile"><span className="kicker">Blocked</span><div className="num">{blocked}</div></div>
      </div>

      {error && <div className="error">{error}</div>}
      {loading && <div className="card muted mono">Loading...</div>}
      {!loading && shops.length === 0 && (
        <div className="card col">
          <strong>You don't have any shops yet.</strong>
          <span className="muted small">Apply to open one — an administrator will review it.</span>
          <button className="primary" onClick={() => navigate('/merchant/apply')}>Open your first shop</button>
        </div>
      )}

      <div className="grid cards">
        {shops.map((s) => (
          <div key={s.merchantId} className="card col selectable">
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
            <Link className="btn primary" to={`/merchant/shops/${s.merchantId}/menu`}>Manage menu</Link>
          </div>
        ))}
      </div>
    </div>
  );
}
