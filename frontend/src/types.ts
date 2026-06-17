// Shared types mirroring the OnTheWay backend DTOs.

export type UserRole = 'USER' | 'MERCHANT' | 'ADMIN';

export type StoreType =
  | 'RESTAURANT' | 'FAST_FOOD' | 'CAFE' | 'BAKERY' | 'PHARMACY' | 'MEDICAL'
  | 'GROCERY' | 'SUPERMARKET' | 'HOTEL' | 'BOOKSTORE' | 'ELECTRONICS'
  | 'HARDWARE' | 'FLORIST' | 'PET_STORE' | 'RETAIL' | 'OTHER';

export type MerchantStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

export type OrderStatus = 'PLACED' | 'PREPARING' | 'READY' | 'PICKED' | 'CANCELLED';

export interface UserResponse {
  userId: number;
  email: string;
  name: string;
  role: UserRole;
}

export interface Shop {
  merchantId: number;
  userId: number;
  storeName: string;
  storeType: StoreType;
  status: MerchantStatus;
  statusReason: string | null;
  address: string;
  latitude: number | null;
  longitude: number | null;
  prepTimeMins: number | null;
  etaBufferMins: number | null;
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

export interface SearchResult {
  menuItemId: number;
  itemName: string;
  description: string | null;
  price: number;
  merchantId: number;
  storeName: string;
  storeType: StoreType;
  address: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  travelMins: number;
}

export interface AdminMetrics {
  totalUsers: number;
  totalCustomers: number;
  totalMerchants: number;
  totalShops: number;
  approvedShops: number;
  pendingShops: number;
  suspendedShops: number;
  rejectedShops: number;
  totalOrders: number;
  ordersByStatus: Record<string, number>;
  grossRevenue: number;
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
  trafficBufferMins: number;
  prepStartAt: string;
  readyAt: string;
  etaEarliest: string;
  etaLatest: string;
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
