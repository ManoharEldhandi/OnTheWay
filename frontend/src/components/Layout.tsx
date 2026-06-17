import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';

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

export function Layout() {
  const { user, logout } = useAuth();

  return (
    <>
      <nav className="nav">
        <div className="brand"><span className="mark" />On<span>The</span>Way</div>
        <div className="links">
          <RoleLinks />
        </div>
        <div className="who">
          <span className="badge steel small">{roleLabel(user?.role)}</span>
          <span className="muted small">{user?.name}</span>
          <button className="ghost" onClick={logout}>Log out</button>
        </div>
      </nav>
      <div className="container">
        <Outlet />
      </div>
    </>
  );
}
