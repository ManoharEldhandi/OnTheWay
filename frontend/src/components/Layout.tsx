import { useCallback, useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { type OrderRealtimeEvent, type RealtimeState, useOrderRealtime } from '../realtime';

interface LiveAlert {
  id: string;
  event: OrderRealtimeEvent;
  message: string;
  receivedAt: Date;
  read: boolean;
}

/** Navigation links shown for each role. */
function RoleLinks() {
  const { user } = useAuth();
  const { lines } = useCart();
  const cartCount = lines.reduce((n, l) => n + l.quantity, 0);

  if (user?.role === 'MERCHANT') {
    return (
      <>
        <NavLink to="/merchant" end>My Shops</NavLink>
        <NavLink to="/merchant/orders">Order Queue</NavLink>
        <NavLink to="/merchant/apply">Open a Shop</NavLink>
      </>
    );
  }
  if (user?.role === 'ADMIN') {
    return (
      <>
        <NavLink to="/admin" end>Overview</NavLink>
        <NavLink to="/admin/approvals">Approvals</NavLink>
        <NavLink to="/admin/shops">Shops</NavLink>
      </>
    );
  }
  // Customer
  return (
    <>
      <NavLink to="/" end>Discover</NavLink>
      <NavLink to="/orders">My Orders</NavLink>
      <NavLink to="/checkout">Cart{cartCount > 0 ? ` (${cartCount})` : ''}</NavLink>
    </>
  );
}

/** Human-readable label for the current role. */
function roleLabel(role?: string): string {
  switch (role) {
    case 'MERCHANT': return 'Merchant';
    case 'ADMIN': return 'Administrator';
    default: return 'Customer';
  }
}

function liveTravelMinutes(etaSegment: string | null): number | null {
  const match = /travel\s+(\d+)\s+min/i.exec(etaSegment ?? '');
  return match ? Number(match[1]) : null;
}

function liveAlertMessage(event: OrderRealtimeEvent, role?: string): string {
  if (event.type === 'ORDER_ETA_CHANGED') {
    const minutes = liveTravelMinutes(event.etaSegment);
    if (role === 'MERCHANT') {
      if (minutes === 0) return `Order #${event.orderId}: customer has arrived at pickup and may be waiting.`;
      if (minutes !== null && minutes <= 5) return `Order #${event.orderId}: customer is approaching · about ${minutes} min away.`;
      if (minutes !== null) return `Order #${event.orderId}: customer is on the way · about ${minutes} min away.`;
      return `Order #${event.orderId}: customer ETA updated.`;
    }
    return `Order #${event.orderId}: your live route timing updated.`;
  }
  if (event.type === 'ORDER_CHAT_MESSAGE') return `Order #${event.orderId}: new message in order chat`;
  if (event.type.startsWith('PAYMENT_')) return `Order #${event.orderId}: ${event.type.replace('PAYMENT_', 'payment ').toLowerCase()}`;
  return `Order #${event.orderId} is ${event.status.toLowerCase()}`;
}

function liveAlertKey(event: OrderRealtimeEvent, role?: string): string {
  if (event.type === 'ORDER_ETA_CHANGED' && role === 'MERCHANT') {
    const minutes = liveTravelMinutes(event.etaSegment);
    const stage = minutes === 0 ? 'arrived' : minutes !== null && minutes <= 5 ? 'approaching' : 'en-route';
    return `${event.type}-${event.orderId}-${stage}`;
  }
  return `${event.type}-${event.orderId}`;
}

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [alerts, setAlerts] = useState<LiveAlert[]>([]);
  const [toast, setToast] = useState<LiveAlert | null>(null);
  const [alertsOpen, setAlertsOpen] = useState(false);
  const [realtimeState, setRealtimeState] = useState<RealtimeState>('connecting');
  const timers = useRef<number[]>([]);
  const lastToastAt = useRef(new Map<string, number>());

  useEffect(() => () => timers.current.forEach((timer) => window.clearTimeout(timer)), []);

  const receiveAlert = useCallback((event: OrderRealtimeEvent) => {
    const message = liveAlertMessage(event, user?.role);
    const id = `${event.type}-${event.orderId}-${Date.now()}`;
    const alertKey = liveAlertKey(event, user?.role);
    const alert: LiveAlert = { id, event, message, receivedAt: new Date(), read: false };
    // ETA checkpoints can be emitted frequently. Keep the alert centre useful by
    // replacing repeats, while preserving the three meaningful merchant stages.
    setAlerts((current) => {
      const duplicate = current.findIndex((candidate) => liveAlertKey(candidate.event, user?.role) === alertKey);
      const withoutDuplicate = duplicate === -1 ? current : current.filter((_, index) => index !== duplicate);
      return [alert, ...withoutDuplicate].slice(0, 8);
    });

    // A toast is a brief, non-interactive signal only. One is ever visible and
    // repeat events for the same order/type do not restart a visual pile-up.
    const toastKey = alertKey;
    const now = Date.now();
    if ((lastToastAt.current.get(toastKey) ?? 0) + 1800 <= now) {
      lastToastAt.current.set(toastKey, now);
      setToast(alert);
      timers.current.push(window.setTimeout(() => {
        setToast((current) => current?.id === id ? null : current);
      }, 2000));
    }

    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      new Notification('OnTheWay', { body: message, tag: `order-${event.orderId}` });
    }
  }, [user?.role]);

  // Persistent socket notifications complement the page-level refresh hooks. They are scoped by
  // the server, so a customer or merchant only ever sees events for accessible orders.
  useOrderRealtime(receiveAlert, setRealtimeState);

  const unreadCount = alerts.filter((alert) => !alert.read).length;
  const realtimeLabel = realtimeState === 'connected' ? 'Live' : realtimeState === 'reconnecting' ? 'Reconnecting' : 'Offline';

  function openAlerts() {
    setAlertsOpen((open) => !open);
    setAlerts((current) => current.map((alert) => ({ ...alert, read: true })));
  }

  async function enableDesktopAlerts() {
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      await Notification.requestPermission();
    }
  }

  function visitAlert(alert: LiveAlert) {
    setAlertsOpen(false);
    if (user?.role === 'MERCHANT') {
      navigate('/merchant/orders');
    } else if (user?.role === 'USER') {
      navigate(`/orders/${alert.event.orderId}`);
    } else {
      navigate('/admin');
    }
  }

  return (
    <>
      <nav className="nav">
        <div className="brand"><span className="mark" /><span className="brand-name">OnTheWay</span></div>
        <div className="links">
          <RoleLinks />
        </div>
        <div className="who">
          <div className="alerts-wrap">
            <button className="alert-button" aria-label="Live order alerts" onClick={openAlerts}>
              <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
              <span className={`live-dot ${realtimeState}`} aria-label={`Realtime: ${realtimeLabel}`} />
              Alerts{unreadCount > 0 ? ` (${unreadCount})` : ''}
            </button>
            {alertsOpen && (
              <div className="alerts-panel" role="status" aria-live="polite">
                <div className="spread"><strong>Live order alerts</strong><span className={`connection-state ${realtimeState}`}>{realtimeLabel}</span></div>
                {alerts.length === 0 ? <span className="muted small">Both signed-in tabs will receive order and route updates here.</span> : alerts.map((alert) => (
                  <button className="alert-item" key={alert.id} onClick={() => visitAlert(alert)}>
                    <span>{alert.message}</span><small>{alert.receivedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small>
                  </button>
                ))}
                <div className="row spread">
                  {typeof Notification !== 'undefined' && Notification.permission !== 'granted'
                    ? <button className="ghost small" onClick={enableDesktopAlerts}>Enable desktop alerts</button>
                    : <span className="muted small">In-app alerts are active.</span>}
                  {alerts.length > 0 && <button className="ghost small" onClick={() => { setAlerts([]); setToast(null); }}>Clear</button>}
                </div>
              </div>
            )}
          </div>
          <span className="badge steel small">{roleLabel(user?.role)}</span>
          <span className="muted small">{user?.name}</span>
          <button className="ghost" onClick={logout}>Log out</button>
        </div>
      </nav>
      <div className="container">
        <Outlet />
      </div>
      <div className="toast-stack" aria-live="polite" aria-atomic="true">
        {toast && (
          <aside className="live-toast" key={toast.id} role="status">
            <span className="live-toast-dot" />
            <span><strong>Live update</strong>{toast.message}</span>
          </aside>
        )}
      </div>
    </>
  );
}
