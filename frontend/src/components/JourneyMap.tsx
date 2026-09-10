import { useMemo } from 'react';
import type { DemoRouteStop } from '../location';
import type { Coordinates } from '../types';

interface JourneyMapProps {
  route: readonly DemoRouteStop[];
  routeIndex: number;
  /** Continuous 0–1 journey progress, driven by requestAnimationFrame in the order screen. */
  progress?: number;
  isPlaying?: boolean;
}

const W = 1200;
const H = 700;
const PAD = 94;

type Point = { x: number; y: number };

function catmullRom(before: Point, start: Point, end: Point, after: Point, t: number): Point {
  const t2 = t * t;
  const t3 = t2 * t;
  return {
    x: .5 * ((2 * start.x) + (-before.x + end.x) * t + (2 * before.x - 5 * start.x + 4 * end.x - after.x) * t2 + (-before.x + 3 * start.x - 3 * end.x + after.x) * t3),
    y: .5 * ((2 * start.y) + (-before.y + end.y) * t + (2 * before.y - 5 * start.y + 4 * end.y - after.y) * t2 + (-before.y + 3 * start.y - 3 * end.y + after.y) * t3),
  };
}

/** A dense path gives the vehicle a continuous, stable course between ETA checkpoints. */
function smoothSamples(points: readonly Point[]): Point[] {
  if (points.length < 3) return [...points];
  const samples: Point[] = [];
  for (let index = 0; index < points.length - 1; index += 1) {
    const before = points[index - 1] ?? points[index];
    const start = points[index];
    const end = points[index + 1];
    const after = points[index + 2] ?? end;
    for (let step = index === 0 ? 0 : 1; step <= 36; step += 1) {
      samples.push(catmullRom(before, start, end, after, step / 36));
    }
  }
  return samples;
}

function pointAlong(samples: readonly Point[], progress: number): Point {
  if (samples.length < 2) return samples[0] ?? { x: W / 2, y: H / 2 };
  const lengths = samples.slice(1).map((point, index) => Math.hypot(point.x - samples[index].x, point.y - samples[index].y));
  const total = lengths.reduce((sum, length) => sum + length, 0);
  let remaining = total * Math.min(1, Math.max(0, progress));
  for (let index = 0; index < lengths.length; index += 1) {
    if (remaining <= lengths[index]) {
      const amount = lengths[index] === 0 ? 0 : remaining / lengths[index];
      return {
        x: samples[index].x + (samples[index + 1].x - samples[index].x) * amount,
        y: samples[index].y + (samples[index + 1].y - samples[index].y) * amount,
      };
    }
    remaining -= lengths[index];
  }
  return samples[samples.length - 1];
}

/** Keep the map metric tied to the order's geographic endpoints, never SVG pixels. */
function routeDistanceKm(from: Coordinates, to: Coordinates): number {
  const earthRadiusKm = 6371;
  const radians = (degrees: number) => degrees * Math.PI / 180;
  const deltaLat = radians(to.latitude - from.latitude);
  const deltaLng = radians(to.longitude - from.longitude);
  const a = Math.sin(deltaLat / 2) ** 2
    + Math.cos(radians(from.latitude)) * Math.cos(radians(to.latitude)) * Math.sin(deltaLng / 2) ** 2;
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * A tile-free product map built from geographic order checkpoints. It deliberately
 * uses the same curve for the line and the moving marker, so the route cannot drift
 * into a separate decorative animation when ETA updates arrive.
 */
export function JourneyMap({ route, routeIndex, progress = 0, isPlaying = false }: JourneyMapProps) {
  const startArea = route[0]?.label === 'Your starting point' ? 'YOUR LIVE ROUTE' : route[0]?.label.toUpperCase();
  const project = useMemo(() => {
    const lats = route.map((point) => point.coords.latitude);
    const lngs = route.map((point) => point.coords.longitude);
    const spanLat = Math.max(Math.max(...lats) - Math.min(...lats), .012);
    const spanLng = Math.max(Math.max(...lngs) - Math.min(...lngs), .012);
    const minLat = Math.min(...lats) - spanLat * .18;
    const maxLat = Math.max(...lats) + spanLat * .18;
    const minLng = Math.min(...lngs) - spanLng * .18;
    const maxLng = Math.max(...lngs) + spanLng * .18;
    return (coords: Coordinates): Point => ({
      x: PAD + ((coords.longitude - minLng) / (maxLng - minLng)) * (W - PAD * 2),
      y: PAD + ((maxLat - coords.latitude) / (maxLat - minLat)) * (H - PAD * 2),
    });
  }, [route]);

  const points = useMemo(() => route.map((stop) => project(stop.coords)), [project, route]);
  const routeSamples = useMemo(() => smoothSamples(points), [points]);
  const routePath = routeSamples.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  const safeIndex = Math.min(Math.max(routeIndex, 0), route.length - 1);
  const markerProgress = Math.min(1, Math.max(0, progress));
  const current = pointAlong(routeSamples, markerProgress);
  const ahead = pointAlong(routeSamples, Math.min(1, markerProgress + .006));
  const heading = Math.atan2(ahead.y - current.y, ahead.x - current.x) * 180 / Math.PI;
  const distanceKm = route.length > 1
    ? routeDistanceKm(route[0].coords, route[route.length - 1].coords)
    : 0;
  const vehicleTransform = `translate(${current.x.toFixed(2)} ${current.y.toFixed(2)}) rotate(${heading.toFixed(2)})`;

  return (
    <div className="journey-map-wrap">
      <svg className="journey-map" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Live route map with a customer vehicle travelling to the pickup counter">
        <defs>
          <linearGradient id="tracking-surface" x1="0" x2="1" y1="0" y2="1"><stop stopColor="#edf1ef" /><stop offset="1" stopColor="#dbe4e2" /></linearGradient>
          <pattern id="tracking-blocks" width="104" height="88" patternUnits="userSpaceOnUse"><rect width="104" height="88" fill="#e4ebea" /><rect x="8" y="9" width="34" height="22" rx="4" fill="#d2dcda" /><rect x="55" y="12" width="39" height="36" rx="4" fill="#d7e0de" /><rect x="18" y="54" width="29" height="22" rx="4" fill="#d5dfdd" /><rect x="63" y="58" width="28" height="17" rx="4" fill="#cfdad7" /></pattern>
          <linearGradient id="tracking-route" x1="0" x2="1"><stop stopColor="#f4511e" /><stop offset="1" stopColor="#ff8a50" /></linearGradient>
          <filter id="tracking-shadow" x="-60%" y="-60%" width="220%" height="220%"><feDropShadow dx="0" dy="6" stdDeviation="7" floodColor="#172033" floodOpacity=".24" /></filter>
          <filter id="tracking-soft-shadow" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="3" stdDeviation="3" floodColor="#172033" floodOpacity=".16" /></filter>
        </defs>

        <rect width={W} height={H} fill="url(#tracking-surface)" />
        <rect width={W} height={H} fill="url(#tracking-blocks)" opacity=".82" />
        <path className="tracking-water" d="M-28 144 C154 72 278 218 434 129 S722 71 877 147 S1063 194 1230 88" />
        <path className="tracking-road major" d="M-30 544 C185 426 302 599 472 506 S760 405 914 496 S1077 554 1230 469" />
        <path className="tracking-road major" d="M148 -28 C222 132 190 237 344 337 S632 454 736 728" />
        <path className="tracking-road ring" d="M64 250 C259 196 332 254 442 332 S704 393 847 306 S1015 259 1160 334" />
        {Array.from({ length: 9 }).map((_, index) => <path key={`tracking-v-${index}`} className="tracking-road minor" d={`M ${-34 + index * 150} -10 C ${46 + index * 147} 184 ${-13 + index * 154} 424 ${76 + index * 159} 718`} />)}
        {Array.from({ length: 7 }).map((_, index) => <path key={`tracking-h-${index}`} className="tracking-road minor" d={`M -10 ${75 + index * 88} C 274 ${15 + index * 86} 523 ${156 + index * 72} 1210 ${45 + index * 85}`} />)}
        <text className="tracking-area" x="75" y="106">{startArea}</text>
        <text className="tracking-area" x="432" y="205">CITY CONNECTOR</text>
        <text className="tracking-area" x="868" y="607">PICKUP AREA</text>
        <g className="tracking-landmark"><circle cx="248" cy="212" r="8" /><text x="264" y="217">Transit hub</text></g>
        <g className="tracking-landmark"><circle cx="767" cy="183" r="8" /><text x="783" y="188">City park</text></g>

        <path d={routePath} className="tracking-route-glow" />
        <path d={routePath} className="tracking-route-outline" />
        <path d={routePath} className="tracking-route-line" />

        {route.map((stop, index) => {
          const point = points[index];
          const destination = index === route.length - 1;
          const start = index === 0;
          const passed = index <= safeIndex;
          const labelToLeft = point.x > W * .68;
          const labelX = point.x + (labelToLeft ? -27 : 27);
          const anchor = labelToLeft ? 'end' : 'start';
          return (
            <g key={`${stop.label}-${index}`} className={`tracking-stop ${passed ? 'passed' : ''} ${destination ? 'destination' : ''}`} filter={destination || start ? 'url(#tracking-soft-shadow)' : undefined}>
              <circle className="tracking-stop-ring" cx={point.x} cy={point.y} r={destination || start ? 20 : 11} />
              <circle className="tracking-stop-dot" cx={point.x} cy={point.y} r={destination || start ? 12 : 6} />
              {destination ? <path className="tracking-shop-icon" d={`M${point.x - 8} ${point.y - 1}h16v11h-16zM${point.x - 11} ${point.y - 1}l11-9 11 9M${point.x - 2} ${point.y + 4}h4v6h-4z`} /> : start ? <path className="tracking-start-icon" d={`M${point.x} ${point.y - 8}l6 14-6-3-6 3z`} /> : <circle className="tracking-stop-core" cx={point.x} cy={point.y} r="2.5" />}
              {(start || destination || index === Math.ceil((route.length - 1) / 2)) && <>
                <text className="tracking-stop-label" x={labelX} y={point.y - 7} textAnchor={anchor}>{start ? 'You' : stop.label}</text>
                <text className="tracking-stop-detail" x={labelX} y={point.y + 10} textAnchor={anchor}>{start ? 'live pickup route' : stop.detail}</text>
              </>}
            </g>
          );
        })}

        <g className={`tracking-vehicle ${isPlaying ? 'moving' : ''}`} data-testid="journey-vehicle" data-route-progress={markerProgress.toFixed(3)} transform={vehicleTransform} filter="url(#tracking-shadow)">
          <circle className="tracking-vehicle-halo" r="30" />
          <g className="tracking-vehicle-mark">
            <path d="M-17-10h22l12 10-12 10h-22l-6-10z" />
            <path className="tracking-vehicle-window" d="M-6-6h10l7 6-7 6H-6z" />
            <path className="tracking-vehicle-arrow" d="M3-5 11 0 3 5z" />
            <circle cx="-10" cy="11" r="3.4" /><circle cx="9" cy="11" r="3.4" />
          </g>
        </g>

        <g className="tracking-chip"><rect x="50" y="48" width="194" height="50" rx="25" /><circle cx="76" cy="73" r="7" /><text x="93" y="78">LIVE PICKUP ROUTE</text></g>
        <g className="tracking-scale"><path d="M 54 649 H 198" /><text x="54" y="674">{distanceKm < 10 ? distanceKm.toFixed(1) : Math.round(distanceKm)} km to pickup</text></g>
      </svg>
      <div className="journey-legend"><span><i className="legend-route" /> Your live route</span><span><i className="legend-shop" /> Pickup counter</span><span className="journey-privacy">Only you can see this route</span></div>
    </div>
  );
}
