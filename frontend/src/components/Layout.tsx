import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';

export function Layout() {
  const { user, logout } = useAuth();
  const { lines } = useCart();
  const cartCount = lines.reduce((n, l) => n + l.quantity, 0);

  return (
    <>
      <nav className="nav">
        <div className="brand"><span className="mark" />On<span>The</span>Way</div>
        <div className="links">
          <NavLink to="/" end>Discover</NavLink>
          <NavLink to="/orders">My Orders</NavLink>
          {user?.role === 'MERCHANT' && <NavLink to="/merchant">Merchant Console</NavLink>}
          <NavLink to="/checkout">Cart{cartCount > 0 ? ` (${cartCount})` : ''}</NavLink>
        </div>
        <div className="who">
          <span className="muted small">{user?.name} · {user?.role}</span>
          <button className="ghost" onClick={logout}>Log out</button>
        </div>
      </nav>
      <div className="container">
        <Outlet />
      </div>
    </>
  );
}
