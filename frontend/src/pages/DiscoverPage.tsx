import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { MapView } from '../components/MapView';
import { getLocation, LOCATION_PRESETS, setLocation } from '../location';
import type { Coordinates, StoreDiscovery, StoreType } from '../types';

const CATEGORIES: (StoreType | 'ALL')[] = ['ALL', 'RESTAURANT', 'CAFE', 'PHARMACY', 'GROCERY', 'BAKERY'];

export function DiscoverPage() {
  const navigate = useNavigate();
  const [coords, setCoords] = useState<Coordinates>(getLocation());
  const [radiusKm, setRadiusKm] = useState(10);
  const [category, setCategory] = useState<StoreType | 'ALL'>('ALL');
  const [stores, setStores] = useState<StoreDiscovery[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function search() {
    setLoading(true);
    setError(null);
    try {
      const q = new URLSearchParams({
        lat: String(coords.latitude),
        lng: String(coords.longitude),
        radiusKm: String(radiusKm),
      });
      if (category !== 'ALL') q.set('category', category);
      const result = await api.get<StoreDiscovery[]>(`/api/discovery/nearby?${q.toString()}`);
      setStores(result);
      setSelected(result[0]?.merchantId ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load stores');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void search();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [coords, radiusKm, category]);

  function pickPreset(c: Coordinates) {
    setLocation(c);
    setCoords(c);
  }

  function useMyLocation() {
    navigator.geolocation?.getCurrentPosition(
      (pos) => pickPreset({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
      () => setError('Could not get your location; using the selected one.'),
    );
  }

  return (
    <div className="col">
      <div>
        <h1 className="title">Stores on your way</h1>
        <p className="sub">Pre-order so it's ready exactly when you arrive.</p>
      </div>

      <div className="card col">
        <div className="row wrap">
          <div className="col grow">
            <label>Your location</label>
            <div className="row wrap">
              {LOCATION_PRESETS.map((p) => (
                <button
                  key={p.label}
                  className={`chip ${coords.latitude === p.coords.latitude ? 'active' : ''}`}
                  onClick={() => pickPreset(p.coords)}
                >
                  {p.label}
                </button>
              ))}
              <button className="chip" onClick={useMyLocation}>📍 Use my location</button>
            </div>
          </div>
          <div className="col">
            <label>Radius: {radiusKm} km</label>
            <input type="range" min={1} max={40} value={radiusKm}
                   onChange={(e) => setRadiusKm(Number(e.target.value))} />
          </div>
        </div>
        <div className="row wrap">
          {CATEGORIES.map((c) => (
            <button key={c} className={`chip ${category === c ? 'active' : ''}`} onClick={() => setCategory(c)}>
              {c === 'ALL' ? 'All' : c.charAt(0) + c.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
      </div>

      <MapView center={coords} stores={stores} selectedId={selected} onSelect={setSelected} />

      {error && <div className="error">{error}</div>}
      {loading && <div className="muted">Searching…</div>}
      {!loading && stores.length === 0 && <div className="muted">No stores within {radiusKm} km. Try a wider radius.</div>}

      <div className="grid cards">
        {stores.map((s) => (
          <div key={s.merchantId} className="card col"
               style={{ outline: selected === s.merchantId ? '2px solid var(--brand-2)' : 'none' }}>
            <div className="spread">
              <strong>{s.storeName}</strong>
              <span className="badge brand">{s.storeType.toLowerCase()}</span>
            </div>
            <span className="muted small">{s.address}</span>
            <div className="row">
              <span className="badge green">{s.distanceKm} km</span>
              <span className="badge">{s.travelMins} min away</span>
              {s.prepTimeMins != null && <span className="badge warn">~{s.prepTimeMins} min prep</span>}
            </div>
            <button className="primary" onClick={() => navigate(`/store/${s.merchantId}`)}>
              View menu
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
