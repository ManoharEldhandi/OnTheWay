import { useEffect, useState } from 'react';
import { api, ApiError } from '../../api/client';
import type { Shop } from '../../types';

export function AdminApprovalsPage() {
  const [pending, setPending] = useState<Shop[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function load() {
    try {
      setPending(await api.get<Shop[]>('/api/admin/applications'));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load applications');
    }
  }

  useEffect(() => { void load(); }, []);

  async function approve(shop: Shop) {
    setBusyId(shop.merchantId);
    setError(null);
    try {
      await api.post(`/api/admin/shops/${shop.merchantId}/approve`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not approve');
    } finally {
      setBusyId(null);
    }
  }

  async function reject(shop: Shop) {
    const reason = window.prompt(`Reject "${shop.storeName}" — reason?`, 'Incomplete details');
    if (!reason) return;
    setBusyId(shop.merchantId);
    setError(null);
    try {
      await api.post(`/api/admin/shops/${shop.merchantId}/reject`, { reason });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reject');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="col">
      <section className="page-head">
        <div className="hero-block motion-line">
          <span className="kicker">Admin queue / shop applications</span>
          <h1 className="title">Approve the map.</h1>
          <p className="sub">Only approved shops become public, searchable, and orderable.</p>
        </div>
        <div className="hero-panel">
          <span className="kicker">Waiting</span>
          <div className="value">{pending.length}</div>
          <span className="badge warn">review queue</span>
        </div>
      </section>

      {error && <div className="error">{error}</div>}
      {pending.length === 0 && <div className="card muted">No applications waiting. All caught up.</div>}

      <div className="grid cards">
        {pending.map((s) => (
          <div key={s.merchantId} className="card col selectable">
            <div className="spread">
              <strong>{s.storeName}</strong>
              <span className="badge steel">{s.storeType.toLowerCase().replace('_', ' ')}</span>
            </div>
            <span className="muted small">{s.address}</span>
            <span className="muted small">
              {s.latitude != null ? `${s.latitude.toFixed(4)}, ${s.longitude?.toFixed(4)}` : 'No location set'}
            </span>
            <div className="row">
              <button className="success grow" disabled={busyId === s.merchantId} onClick={() => approve(s)}>
                Approve
              </button>
              <button className="ghost" disabled={busyId === s.merchantId} onClick={() => reject(s)}>
                Reject
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
