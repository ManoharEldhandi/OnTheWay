import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import type { AdminMetrics } from '../../types';

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card col" style={{ gap: 4 }}>
      <span className="muted small">{label}</span>
      <span className="eta-metric"><span className="value" style={{ fontSize: 26 }}>{value}</span></span>
    </div>
  );
}

export function AdminDashboardPage() {
  const [metrics, setMetrics] = useState<AdminMetrics | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        setMetrics(await api.get<AdminMetrics>('/api/admin/metrics'));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load metrics');
      }
    })();
  }, []);

  return (
    <div className="col">
      <div className="spread">
        <div>
          <h1 className="title">Platform overview</h1>
          <p className="sub">Health and activity across the marketplace.</p>
        </div>
        {metrics && metrics.pendingShops > 0 && (
          <Link className="btn primary" to="/admin/approvals">
            Review {metrics.pendingShops} pending
          </Link>
        )}
      </div>

      {error && <div className="error">{error}</div>}
      {!metrics && !error && <div className="muted">Loading…</div>}

      {metrics && (
        <>
          <h3 className="section-title">People & shops</h3>
          <div className="grid cards">
            <Stat label="Customers" value={metrics.totalCustomers} />
            <Stat label="Merchants" value={metrics.totalMerchants} />
            <Stat label="Total shops" value={metrics.totalShops} />
            <Stat label="Approved shops" value={metrics.approvedShops} />
            <Stat label="Pending approval" value={metrics.pendingShops} />
            <Stat label="Suspended" value={metrics.suspendedShops} />
          </div>

          <h3 className="section-title">Orders & revenue</h3>
          <div className="grid cards">
            <Stat label="Total orders" value={metrics.totalOrders} />
            <Stat label="Gross revenue" value={`₹${metrics.grossRevenue.toFixed(0)}`} />
            {Object.entries(metrics.ordersByStatus).map(([status, count]) => (
              <Stat key={status} label={`Orders · ${status}`} value={count} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
