# Product UI Redesign

The frontend now uses a stronger product visual system: Swiss grid structure, raw black/white surfaces,
a yellow signal color, and cyan/blue complementary accents for motion, focus, and hover states.

## Direction

- **Black/white/yellow** is the core palette; yellow is reserved for primary action and system signal.
- **Complementary cyan/blue** appears in focus rings, shadows, and hover motion so the UI feels alive
  without becoming a purple/gradient dashboard.
- **Swiss-style hierarchy**: large direct headlines, mono labels, metric tiles, hard borders, and
  clear grids.
- **Raw/anti-polish aesthetic**: square geometry, offset shadows, visible structure, no soft SaaS
  blur cards or generic gradients.

## Role-specific surfaces

### Customer
- Command-center discovery hero.
- Search across items and shops.
- Visible count/radius/vertical summary.
- Default radius widened to 20 km so the demo starts populated.
- Checkout and order tracking show live ETA windows in the same visual language.

### Merchant
- Multi-shop operations board.
- Metrics for approved, pending, blocked shops.
- Menu control board with item/in-stock/out-of-stock counts.
- Order queue board with active/preparing/ready/closed metrics.

### Admin
- Marketplace control-room overview.
- Metrics rendered as sharp tiles.
- Approval queue and shop moderation use the same command-board treatment.

## Verification

- `npm run build` passes after the redesign.
- Browser-smoked in demo mode at `http://127.0.0.1:5173` across customer, merchant, and admin dashboards.
- Fixed a real CORS gap: default and demo profiles now include `http://127.0.0.1:5173` in allowed origins.
