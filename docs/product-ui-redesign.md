# Product interface

The OnTheWay interface is built around a calm, high-contrast operational system: neutral surfaces, one focused blue action color, precise typography, and explicit status language. It is designed to make route-aware pickup understandable at a glance rather than to look like a decorative marketplace.

## Direction

- **Quiet, legible surfaces**: white and soft-gray panels let order state, maps, and timing data carry the visual emphasis.
- **Purposeful blue and orange**: blue communicates navigation and action; orange is reserved for the active pickup route and vehicle movement.
- **Map-first customer layout**: discovery starts with nearby shops on a large, usable neighborhood map; cards complement the map instead of forming an endless feed.
- **Operational merchant layout**: a compact queue emphasizes paid state, preparation status, ETA, and the next legal action.
- **Accessible feedback**: meaningful labels accompany color, transient notifications never pile up, and the alert centre retains actionable history.

## Role-specific surfaces

### Customer

- Nearby-shop discovery with search, radius, category filters, and map pins that open the relevant menu.
- Transparent ETA checkout showing travel, traffic allowance, preparation duration, target readiness, and the arrival window.
- A customer-only route surface with a smoothly animated vehicle, live order status, pickup progress, and active-order chat.

### Merchant

- Multi-shop management for menus, stock, pricing, and shop applications.
- A live order queue that begins preparation when a paid order is accepted.
- Privacy-aware arrival signals: en route, approaching, and arrived. The merchant receives timing only, never a customer route or coordinates.

### Administrator

- Marketplace oversight with approval, moderation, and status controls.
- Shared visual language for counts, status, and next actions across the operating surfaces.

## Verification

- `npm run build` validates strict TypeScript and produces the production client bundle.
- Browser verification covers customer discovery, checkout, live route tracking, merchant acceptance, live ETA status, realtime notifications, and order chat.
- The local and network launch modes use explicit CORS origins so the same product flow works on a second device without proxy changes.
