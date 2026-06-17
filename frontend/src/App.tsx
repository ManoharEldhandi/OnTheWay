import { Navigate, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { Layout } from './components/Layout';
import { useAuth } from './auth/AuthContext';
import type { UserRole } from './types';
import { LoginPage } from './pages/LoginPage';
// Customer
import { DiscoverPage } from './pages/DiscoverPage';
import { StorePage } from './pages/StorePage';
import { CheckoutPage } from './pages/CheckoutPage';
import { OrderPage } from './pages/OrderPage';
import { OrdersPage } from './pages/OrdersPage';
// Merchant
import { MerchantShopsPage } from './pages/merchant/MerchantShopsPage';
import { MerchantShopMenuPage } from './pages/merchant/MerchantShopMenuPage';
import { MerchantOrdersPage } from './pages/merchant/MerchantOrdersPage';
import { MerchantApplyPage } from './pages/merchant/MerchantApplyPage';
// Admin
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminApprovalsPage } from './pages/admin/AdminApprovalsPage';
import { AdminShopsPage } from './pages/admin/AdminShopsPage';

/** The landing route for each role. */
export function homeFor(role: UserRole): string {
  switch (role) {
    case 'MERCHANT': return '/merchant';
    case 'ADMIN': return '/admin';
    default: return '/';
  }
}

/** Requires authentication, and optionally a specific role. */
function Require({ role, children }: { role?: UserRole; children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="center muted">Loading…</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (role && user.role !== role) return <Navigate to={homeFor(user.role)} replace />;
  return <>{children}</>;
}

export default function App() {
  const { user } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Customer experience */}
      <Route element={<Require role="USER"><Layout /></Require>}>
        <Route path="/" element={<DiscoverPage />} />
        <Route path="/store/:merchantId" element={<StorePage />} />
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/orders/:orderId" element={<OrderPage />} />
      </Route>

      {/* Merchant dashboard */}
      <Route element={<Require role="MERCHANT"><Layout /></Require>}>
        <Route path="/merchant" element={<MerchantShopsPage />} />
        <Route path="/merchant/apply" element={<MerchantApplyPage />} />
        <Route path="/merchant/shops/:shopId/menu" element={<MerchantShopMenuPage />} />
        <Route path="/merchant/orders" element={<MerchantOrdersPage />} />
      </Route>

      {/* Admin console */}
      <Route element={<Require role="ADMIN"><Layout /></Require>}>
        <Route path="/admin" element={<AdminDashboardPage />} />
        <Route path="/admin/approvals" element={<AdminApprovalsPage />} />
        <Route path="/admin/shops" element={<AdminShopsPage />} />
      </Route>

      {/* Fallback: send to the right home for the current role (or login). */}
      <Route path="*" element={<Navigate to={user ? homeFor(user.role) : '/login'} replace />} />
    </Routes>
  );
}
