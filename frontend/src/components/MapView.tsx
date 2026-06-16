import { useMemo } from 'react';
import type { Coordinates, StoreDiscovery } from '../types';

interface MapViewProps {
  center: Coordinates;
  stores: StoreDiscovery[];
  selectedId?: number | null;
  onSelect?: (merchantId: number) => void;
  height?: number;
}

// A lightweight, dependency-free map: it projects lat/lng around the customer onto
// an SVG canvas. It needs no tiles, no API key, and works fully offline — ideal for
// a reliable product demo while clearly communicating distance and direction.
export function MapView({ center, stores, selectedId, onSelect, height = 320 }: MapViewProps) {
  const W = 640;
  const H = height;
  const pad = 40;

  const { project, selected } = useMemo(() => {
    const lats = [center.latitude, ...stores.map((s) => s.latitude)];
    const lngs = [center.longitude, ...stores.map((s) => s.longitude)];
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
    const selected = stores.find((s) => s.merchantId === selectedId) ?? null;
    return { project, selected };
  }, [center, stores, selectedId, H]);

  const me = project(center);

  return (
    <svg className="card" viewBox={`0 0 ${W} ${H}`} width="100%" height={H} role="img" aria-label="Map of nearby stores">
      <defs>
        <radialGradient id="meGlow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#64748b" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#64748b" stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect x="0" y="0" width={W} height={H} fill="#f4f5f7" rx="12" />

      {/* subtle grid */}
      {Array.from({ length: 8 }).map((_, i) => (
        <line key={`v${i}`} x1={(W / 8) * i} y1="0" x2={(W / 8) * i} y2={H} stroke="#e3e6ea" strokeWidth="1" />
      ))}
      {Array.from({ length: 5 }).map((_, i) => (
        <line key={`h${i}`} x1="0" y1={(H / 5) * i} x2={W} y2={(H / 5) * i} stroke="#e3e6ea" strokeWidth="1" />
      ))}

      {/* route to selected store */}
      {selected && (
        <line
          x1={me.x} y1={me.y}
          x2={project(selected).x} y2={project(selected).y}
          stroke="#4b5563" strokeWidth="2.5" strokeDasharray="6 6"
        />
      )}

      {/* store pins */}
      {stores.map((s) => {
        const p = project(s);
        const isSel = s.merchantId === selectedId;
        return (
          <g key={s.merchantId} style={{ cursor: onSelect ? 'pointer' : 'default' }}
             onClick={() => onSelect?.(s.merchantId)}>
            <circle cx={p.x} cy={p.y} r={isSel ? 9 : 7}
                    fill={isSel ? '#3a8c6e' : '#64748b'} stroke="#ffffff" strokeWidth="2" />
            <text x={p.x + 12} y={p.y + 4} fill="#1f2630" fontSize="12" fontWeight="600">
              {s.storeName}
            </text>
            <text x={p.x + 12} y={p.y + 19} fill="#6b7280" fontSize="11">
              {s.distanceKm} km · {s.travelMins} min
            </text>
          </g>
        );
      })}

      {/* the customer */}
      <circle cx={me.x} cy={me.y} r="22" fill="url(#meGlow)" />
      <circle cx={me.x} cy={me.y} r="6" fill="#ffffff" stroke="#4b5563" strokeWidth="3" />
      <text x={me.x + 12} y={me.y - 10} fill="#6b7280" fontSize="11" fontWeight="700">You</text>
    </svg>
  );
}
