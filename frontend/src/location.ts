import type { Coordinates } from './types';

// The customer's "current location" used for discovery and ETA. Persisted so it
// survives navigation. Defaults to a demo location (central Bengaluru).
const KEY = 'ontheway.location';

export const DEFAULT_LOCATION: Coordinates = { latitude: 12.9716, longitude: 77.5946 };

export const LOCATION_PRESETS: { label: string; coords: Coordinates }[] = [
  { label: 'MG Road (Bengaluru)', coords: { latitude: 12.9716, longitude: 77.5946 } },
  { label: 'Whitefield (15 km)', coords: { latitude: 12.9698, longitude: 77.7499 } },
  { label: 'Airport (35 km)', coords: { latitude: 13.1986, longitude: 77.7066 } },
];

export function getLocation(): Coordinates {
  const raw = localStorage.getItem(KEY);
  if (!raw) return DEFAULT_LOCATION;
  try {
    return JSON.parse(raw) as Coordinates;
  } catch {
    return DEFAULT_LOCATION;
  }
}

export function setLocation(coords: Coordinates): void {
  localStorage.setItem(KEY, JSON.stringify(coords));
}
