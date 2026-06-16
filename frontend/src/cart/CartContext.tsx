import { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { MenuItem } from '../types';

export interface CartLine {
  item: MenuItem;
  quantity: number;
}

interface CartState {
  merchantId: number | null;
  lines: CartLine[];
  total: number;
  add: (item: MenuItem) => void;
  remove: (menuItemId: number) => void;
  clear: () => void;
}

const CartContext = createContext<CartState | undefined>(undefined);

export function CartProvider({ children }: { children: ReactNode }) {
  const [merchantId, setMerchantId] = useState<number | null>(null);
  const [lines, setLines] = useState<CartLine[]>([]);

  function add(item: MenuItem) {
    setLines((prev) => {
      // A cart belongs to one store; switching stores starts a fresh cart.
      if (merchantId !== null && merchantId !== item.merchantId) {
        setMerchantId(item.merchantId);
        return [{ item, quantity: 1 }];
      }
      if (merchantId === null) setMerchantId(item.merchantId);
      const existing = prev.find((l) => l.item.menuItemId === item.menuItemId);
      if (existing) {
        return prev.map((l) =>
          l.item.menuItemId === item.menuItemId ? { ...l, quantity: l.quantity + 1 } : l,
        );
      }
      return [...prev, { item, quantity: 1 }];
    });
  }

  function remove(menuItemId: number) {
    setLines((prev) => {
      const next = prev
        .map((l) => (l.item.menuItemId === menuItemId ? { ...l, quantity: l.quantity - 1 } : l))
        .filter((l) => l.quantity > 0);
      if (next.length === 0) setMerchantId(null);
      return next;
    });
  }

  function clear() {
    setLines([]);
    setMerchantId(null);
  }

  const total = lines.reduce((sum, l) => sum + l.item.price * l.quantity, 0);

  const value = useMemo(
    () => ({ merchantId, lines, total, add, remove, clear }),
    [merchantId, lines, total],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within CartProvider');
  return ctx;
}
