import { useMemo } from 'react';
import type { Coordinates, StoreDiscovery } from '../types';

interface MapViewProps {
  center: Coordinates;
  stores: StoreDiscovery[];
  selectedId?: number | null;
  onSelect?: (merchantId: number) => void;
  /** Opens the same store menu as the corresponding result card. */
  onOpen?: (merchantId: number) => void;
  height?: number;
}

type PinKind = 'cup' | 'health' | 'basket' | 'meal' | 'shop';
type Point = { x: number; y: number };

function routeCurve(from: Point, to: Point): string {
  const midpoint = { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 };
  const distance = Math.hypot(to.x - from.x, to.y - from.y) || 1;
  const bend = Math.min(74, Math.max(28, distance * .16));
  const control = {
    x: midpoint.x + (to.y - from.y) / distance * bend,
    y: midpoint.y - (to.x - from.x) / distance * bend,
  };
  return `M ${from.x} ${from.y} Q ${control.x} ${control.y} ${to.x} ${to.y}`;
}

function pinKind(storeType: StoreDiscovery['storeType']): PinKind {
  if (storeType === 'CAFE' || storeType === 'BAKERY') return 'cup';
  if (storeType === 'PHARMACY' || storeType === 'MEDICAL') return 'health';
  if (storeType === 'GROCERY' || storeType === 'SUPERMARKET') return 'basket';
  if (storeType === 'RESTAURANT' || storeType === 'FAST_FOOD' || storeType === 'HOTEL') return 'meal';
  return 'shop';
}

/** A small dependency-free icon system for map pins; no emoji or external icon font required. */
function StorePinSymbol({ kind, selected }: { kind: PinKind; selected: boolean }) {
  const color = selected ? '#f4511e' : '#53627a';
  if (kind === 'health') return <><rect x="-8" y="-8" width="16" height="16" rx="5" fill={color} /><path d="M-4 0h8M0-4v8" stroke="#fff" strokeWidth="2" strokeLinecap="round" /></>;
  if (kind === 'basket') return <><path d="M-8-2h16l-2 9H-6zM-4-2l2-4h4l2 4" fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" /><path d="M-3 2h6" stroke={color} strokeWidth="2" strokeLinecap="round" /></>;
  if (kind === 'cup') return <><path d="M-7-5h12v10a5 5 0 0 1-10 0V-5zM5-2h3a3 3 0 0 1 0 6H5" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /><path d="M-3-9c-2 2 2 3 0 5M2-9c-2 2 2 3 0 5" fill="none" stroke={color} strokeWidth="1.4" strokeLinecap="round" /></>;
  if (kind === 'meal') return <><path d="M-6-8v16M-9-8v5a3 3 0 0 0 6 0v-5M4-8v16M4-8c4 3 4 7 0 8" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" /></>;
  return <><path d="M-9-1 0-8 9-1v9H-9z" fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" /><path d="M-2 8V2h4v6" fill="none" stroke={color} strokeWidth="2" /></>;
}

// A lightweight, dependency-free map: it projects lat/lng around the customer onto
// an SVG canvas. It needs no tiles, no API key, and works fully offline — ideal for
// a reliable product demo while clearly communicating distance and direction.
export function MapView({ center, stores, selectedId, onSelect, onOpen, height = 320 }: MapViewProps) {
  const W = 640;
  const H = height;
  const pad = 40;

  // A wide discovery radius can contain more than a hundred shops. Plotting all of
  // their labels turns the map into noise, so it is deliberately a nearby-marker
  // overview. The selected shop is always retained even when it is outside this set.
  const visibleStores = useMemo(() => {
    const nearest = [...stores].sort((a, b) => a.distanceKm - b.distanceKm).slice(0, 18);
    const selectedStore = stores.find((store) => store.merchantId === selectedId);
    if (selectedStore && !nearest.some((store) => store.merchantId === selectedStore.merchantId)) {
      return [...nearest.slice(0, 17), selectedStore];
    }
    return nearest;
  }, [stores, selectedId]);

  const { project, selected } = useMemo(() => {
    const lats = [center.latitude, ...visibleStores.map((s) => s.latitude)];
    const lngs = [center.longitude, ...visibleStores.map((s) => s.longitude)];
    let minLat = Math.min(...lats), maxLat = Math.max(...lats);
    let minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
    // Avoid a zero-size box when everything is co-located.
    const spanLat = Math.max(maxLat - minLat, 0.01);
    const spanLng = Math.max(maxLng - minLng, 0.01);
    minLat -= spanLat * 0.15; maxLat += spanLat * 0.15;
    minLng -= spanLng * 0.15; maxLng += spanLng * 0.15;

    const project = (c: Coordinates) => {
      const x = pad + ((c.longitude - minLng) / (maxLng - minLng)) * (W - 2 * pad);
      // invert y so north is up
      const y = pad + ((maxLat - c.latitude) / (maxLat - minLat)) * (H - 2 * pad);
      return { x, y };
    };
    const selected = visibleStores.find((s) => s.merchantId === selectedId) ?? null;
    return { project, selected };
  }, [center, visibleStores, selectedId, H]);

  const me = project(center);

  const selectedPoint = selected ? project(selected) : null;
  const selectedLabelToLeft = selectedPoint ? selectedPoint.x > W * .66 : false;

  return (
    <svg className="discovery-map" viewBox={`0 0 ${W} ${H}`} width="100%" height={H} role="img" aria-label="Map of nearby shops and the selected pickup route">
      <defs>
        <linearGradient id="discovery-surface" x1="0" x2="1" y1="0" y2="1">
          <stop stopColor="#eef3f7" /><stop offset="1" stopColor="#e3ebf1" />
        </linearGradient>
        <pattern id="discovery-blocks" width="88" height="70" patternUnits="userSpaceOnUse">
          <rect width="88" height="70" fill="#eaf0f3" />
          <rect x="7" y="8" width="31" height="17" rx="3" fill="#dce6ea" />
          <rect x="49" y="11" width="28" height="32" rx="3" fill="#dfe8eb" />
          <rect x="14" y="42" width="26" height="18" rx="3" fill="#e0e8e9" />
        </pattern>
        <marker id="routeArrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
          <path d="M0,0 L0,6 L7,3 z" fill="#345661" />
        </marker>
        <filter id="discovery-shadow" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="3" stdDeviation="3" floodColor="#172033" floodOpacity=".18" /></filter>
      </defs>
      <rect x="0" y="0" width={W} height={H} fill="url(#discovery-surface)" rx="18" />
      <rect x="0" y="0" width={W} height={H} fill="url(#discovery-blocks)" opacity=".72" rx="18" />
      <path className="discovery-water" d={`M-20 ${H * .18} C ${W * .16} ${H * .04} ${W * .25} ${H * .31} ${W * .43} ${H * .15} S ${W * .78} ${H * .11} ${W + 30} ${H * .28}`} />
      <path className="discovery-road main" d={`M-28 ${H * .72} C ${W * .14} ${H * .55} ${W * .24} ${H * .91} ${W * .43} ${H * .7} S ${W * .77} ${H * .48} ${W + 28} ${H * .73}`} />
      <path className="discovery-road main" d={`M${W * .13} -20 C ${W * .19} ${H * .26} ${W * .35} ${H * .35} ${W * .44} ${H * .55} S ${W * .66} ${H * .87} ${W * .73} ${H + 20}`} />
      {Array.from({ length: 8 }).map((_, index) => <path key={`vertical-road-${index}`} className="discovery-road" d={`M${-20 + index * 96} 0 C${54 + index * 91} ${H * .26} ${-8 + index * 97} ${H * .67} ${62 + index * 104} ${H + 20}`} />)}
      {Array.from({ length: 5 }).map((_, index) => <path key={`horizontal-road-${index}`} className="discovery-road" d={`M0 ${54 + index * 78} C${W * .25} ${12 + index * 87} ${W * .56} ${H * .35 + index * 28} ${W} ${32 + index * 81}`} />)}
      <text className="discovery-area" x="55" y="76">YOUR NEIGHBOURHOOD</text>
      <text className="discovery-area" x={W - 235} y={H - 48}>PICKUP DISTRICT</text>

      {/* route to selected store */}
      {selectedPoint && (
        <>
          <path d={routeCurve(me, selectedPoint)} className="discovery-route-shadow" />
          <path d={routeCurve(me, selectedPoint)} className="discovery-route" markerEnd="url(#routeArrow)" />
        </>
      )}

      {/* store pins */}
      {visibleStores.map((s) => {
        const p = project(s);
        const isSel = s.merchantId === selectedId;
        return (
          <g key={s.merchantId} role={onOpen ? "button" : undefined} tabIndex={onOpen ? 0 : undefined}
             style={{ cursor: onSelect || onOpen ? 'pointer' : 'default' }}
             onClick={() => { onSelect?.(s.merchantId); onOpen?.(s.merchantId); }}
             onKeyDown={(event) => {
               if (onOpen && (event.key === 'Enter' || event.key === ' ')) {
                 event.preventDefault();
                 onSelect?.(s.merchantId);
                 onOpen(s.merchantId);
               }
             }}>
            <title>{`${s.storeName} · ${s.distanceKm} km · ${s.travelMins} min away · open menu`}</title>
            <g transform={`translate(${p.x} ${p.y})`} filter={isSel ? 'url(#discovery-shadow)' : undefined}>
              <circle r={isSel ? 16 : 13} fill={isSel ? '#fff4ef' : '#ffffff'} stroke={isSel ? '#f4511e' : '#c4d0d7'} strokeWidth="2" />
              <StorePinSymbol kind={pinKind(s.storeType)} selected={isSel} />
            </g>
            {isSel && <>
              <rect className="selected-shop-label-bg" x={selectedLabelToLeft ? p.x - 174 : p.x + 20} y={p.y - 28} width="154" height="43" rx="8" />
              <text x={selectedLabelToLeft ? p.x - 166 : p.x + 28} y={p.y - 10} fill="#1f2630" fontSize="12" fontWeight="800">
                {s.storeName}
              </text>
              <text x={selectedLabelToLeft ? p.x - 166 : p.x + 28} y={p.y + 4} fill="#53627a" fontSize="10.5">
                {s.distanceKm} km · {s.travelMins} min away
              </text>
              <text x={selectedLabelToLeft ? p.x - 166 : p.x + 28} y={p.y + 16} fill="#345661" fontSize="9.5" fontWeight="700">
                {onOpen ? 'Tap pin to open menu' : 'Selected pickup point'}
              </text>
            </>}
          </g>
        );
      })}

      {/* the customer */}
      <g transform={`translate(${me.x} ${me.y})`} filter="url(#discovery-shadow)">
        <circle r="18" fill="#ffffff" stroke="#172033" strokeWidth="2" />
        <path d="M0-10 7 7 0 3-7 7z" fill="#172033" stroke="#ffffff" strokeWidth="1.7" strokeLinejoin="round" />
      </g>
      <text x={me.x + 22} y={me.y + 4} fill="#29364d" fontSize="11" fontWeight="800">You</text>
    </svg>
  );
}
