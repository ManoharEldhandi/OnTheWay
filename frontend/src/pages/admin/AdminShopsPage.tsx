import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '../../api/client';
import type { MerchantStatus, Shop } from '../../types';

const FILTERS: (MerchantStatus | 'ALL')[] = ['ALL', 'APPROVED', 'PENDING', 'SUSPENDED', 'REJECTED'];

const STATUS_BADGE: Record<string, string> = {
  APPROVED: 'ok', PENDING: 'warn', SUSPENDED: 'warn', REJECTED: 'warn',
};

export function AdminShopsPage() {
  const [shops, setShops] = useState<Shop[]>([]);
  const [filter, setFilter] = useState<MerchantStatus | 'ALL'>('ALL');
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const q = filter === 'ALL' ? '' : `?status=${filter}`;
      setShops(await api.get<Shop[]>(`/api/admin/shops${q}`));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load shops');
    }
  }, [filter]);

  useEffect(() => { void load(); }, [load]);

  async function act(shop: Shop, action: 'suspend' | 'reactivate' | 'delete') {
    setError(null);
    setBusyId(shop.merchantId);
    try {
      if (action === 'suspend') {
        const reason = window.prompt(`Suspend "${shop.storeName}" — reason?`, 'Policy review');
        if (!reason) { setBusyId(null); return; }
        await api.post(`/api/admin/shops/${shop.merchantId}/suspend`, { reason });
      } else if (action === 'reactivate') {
        await api.post(`/api/admin/shops/${shop.merchantId}/reactivate`);
      } else {
        if (!window.confirm(`Permanently delete "${shop.storeName}"?`)) { setBusyId(null); return; }
        await api.del(`/api/admin/shops/${shop.merchantId}`);
      }
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Action failed');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="col">
      <div>
        <h1 className="title">Shops</h1>
        <p className="sub">Moderate the marketplace: suspend, reactivate, or remove shops.</p>
      </div>

      <div className="row wrap">
        {FILTERS.map((f) => (
          <button key={f} className={`chip ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>
            {f === 'ALL' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {error && <div className="error">{error}</div>}

      <div className="grid cards">
        {shops.map((s) => (
          <div key={s.merchantId} className="card col">
            <div className="spread">
              <strong>{s.storeName}</strong>
              <span className={`badge ${STATUS_BADGE[s.status] ?? ''}`}>{s.status}</span>
            </div>
            <span className="badge steel">{s.storeType.toLowerCase().replace('_', ' ')}</span>
            <span className="muted small">{s.address}</span>
            <div className="row wrap">
              {s.status === 'APPROVED' && (
                <button disabled={busyId === s.merchantId} onClick={() => act(s, 'suspend')}>Suspend</button>
              )}
              {s.status === 'SUSPENDED' && (
                <button className="success" disabled={busyId === s.merchantId} onClick={() => act(s, 'reactivate')}>
                  Reactivate
                </button>
              )}
              <button className="ghost" disabled={busyId === s.merchantId} onClick={() => act(s, 'delete')}>
                Delete
              </button>
            </div>
          </div>
        ))}
        {shops.length === 0 && <div className="muted">No shops for this filter.</div>}
      </div>
    </div>
  );
}
