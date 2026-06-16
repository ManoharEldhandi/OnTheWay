// Shared types mirroring the OnTheWay backend DTOs.

export type UserRole = 'USER' | 'MERCHANT' | 'ADMIN';

export type StoreType =
  | 'RESTAURANT' | 'CAFE' | 'PHARMACY' | 'GROCERY'
  | 'BAKERY' | 'RETAIL' | 'ELECTRONICS' | 'FLORIST' | 'OTHER';

export type OrderStatus = 'PLACED' | 'PREPARING' | 'READY' | 'PICKED' | 'CANCELLED';

export interface UserResponse {
  userId: number;
  email: string;
  name: string;
  role: UserRole;
}

export interface StoreDiscovery {
  merchantId: number;
  storeName: string;
  storeType: StoreType;
  address: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  travelMins: number;
  prepTimeMins: number | null;
}

export interface MenuItem {
  menuItemId: number;
  merchantId: number;
  name: string;
  description: string | null;
  price: number;
  availability: boolean;
}

export interface EtaQuote {
  merchantId: number;
  distanceKm: number;
  travelMins: number;
  prepTimeMins: number;
  bufferMins: number;
  prepStartAt: string;
  readyAt: string;
}

export interface OrderItemResponse {
  orderItemId: number;
  menuItemId: number;
  quantity: number;
  priceEach: number;
  totalPrice: number;
}

export interface OrderResponse {
  orderId: number;
  userId: number;
  merchantId: number;
  orderTime: string;
  pickupTime: string;
  etaSegment: string | null;
  status: OrderStatus;
  totalAmount: number;
  items: OrderItemResponse[];
}

export interface Coordinates {
  latitude: number;
  longitude: number;
}
