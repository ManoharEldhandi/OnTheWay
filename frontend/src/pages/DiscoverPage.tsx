import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { MapView } from '../components/MapView';
import { getLocation, LOCATION_PRESETS, setLocation } from '../location';
import type { Coordinates, SearchResult, StoreDiscovery, StoreType } from '../types';

const CATEGORIES: (StoreType | 'ALL')[] = [
  'ALL', 'CAFE', 'RESTAURANT', 'PHARMACY', 'GROCERY', 'ELECTRONICS',
];

type Sort = 'relevance' | 'distance' | 'price';

function label(c: StoreType | 'ALL'): string {
  return c === 'ALL' ? 'All' : c.charAt(0) + c.slice(1).toLowerCase().replace('_', ' ');
}

export function DiscoverPage() {
  const navigate = useNavigate();
  const [coords, setCoords] = useState<Coordinates>(getLocation());
  const [radiusKm, setRadiusKm] = useState(20);
  const [category, setCategory] = useState<StoreType | 'ALL'>('ALL');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<Sort>('relevance');

  const [stores, setStores] = useState<StoreDiscovery[]>([]);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [visibleLimit, setVisibleLimit] = useState(5);

  const searching = query.trim().length > 0;

  async function run() {
    setLoading(true);
    setError(null);
    try {
      const p = new URLSearchParams({
        lat: String(coords.latitude), lng: String(coords.longitude), radiusKm: String(radiusKm),
      });
      if (category !== 'ALL') p.set('category', category);

      if (searching) {
        p.set('q', query.trim());
        p.set('sort', sort);
        const r = await api.get<SearchResult[]>(`/api/discovery/search?${p.toString()}`);
        setResults(r);
        setSelected(r[0]?.merchantId ?? null);
      } else {
        const r = await api.get<StoreDiscovery[]>(`/api/discovery/nearby?${p.toString()}`);
        setStores(r);
        setSelected(r[0]?.merchantId ?? null);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Search failed');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [coords, radiusKm, category, query, sort]);

  // Keep the nearby experience browsable even when a wide radius returns the
  // complete catalogue. A user can deliberately request more results instead
  // of being handed an endless, unusable vertical column.
  useEffect(() => setVisibleLimit(5), [coords, radiusKm, category, query, sort]);

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

  // Map markers come from whichever result set is active (deduplicated by shop).
  const markers: StoreDiscovery[] = searching
    ? dedupeShops(results)
    : stores;
  const visibleCount = searching ? results.length : stores.length;
  const visibleStores = stores.slice(0, visibleLimit);
  const visibleResults = results.slice(0, visibleLimit);

  return (
    <main className="customer-discover col">
      <section className="discover-intro">
        <div>
          <span className="kicker">Nearby pickup</span>
          <h1>What are you picking up today?</h1>
          <p>Browse food, pharmacy, groceries, and everyday essentials around your selected location.</p>
        </div>
        <div className="discover-count" aria-live="polite">
          <span>{loading ? 'Finding shops' : `${visibleCount} places nearby`}</span>
          <small>{category === 'ALL' ? 'All categories' : label(category)} · {radiusKm} km</small>
        </div>
      </section>

      <section className="discovery-command card">
        {/* Search bar over shops AND items */}
        <div className="discover-search row">
          <input
            aria-label="Search shops and items"
            placeholder="Search biryani, paracetamol, coffee, groceries…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {searching && <button className="ghost" onClick={() => setQuery('')}>Clear</button>}
        </div>

        <div className="filters-grid discover-filters">
          <div className="col">
            <label>Your location</label>
            <div className="row wrap">
              {LOCATION_PRESETS.map((p) => (
                <button key={p.label}
                  className={`chip ${coords.latitude === p.coords.latitude ? 'active' : ''}`}
                  onClick={() => pickPreset(p.coords)}>
                  {p.label}
                </button>
              ))}
              <button className="chip" onClick={useMyLocation}>Use my location</button>
            </div>
          </div>
          <div className="col">
            <label>Radius: {radiusKm} km</label>
            <input type="range" min={1} max={40} value={radiusKm}
              onChange={(e) => setRadiusKm(Number(e.target.value))} />
          </div>
        </div>

        <div className="discover-category-row" aria-label="Store categories">
          {CATEGORIES.map((c) => (
            <button key={c} className={`chip ${category === c ? 'active' : ''}`} onClick={() => setCategory(c)}>
              {label(c)}
            </button>
          ))}
        </div>

        {searching && (
          <div className="row wrap discover-sort">
            <span className="muted small">Sort by</span>
            {(['relevance', 'distance', 'price'] as Sort[]).map((s) => (
              <button key={s} className={`chip ${sort === s ? 'active' : ''}`} onClick={() => setSort(s)}>
                {s.charAt(0).toUpperCase() + s.slice(1)}
              </button>
            ))}
          </div>
        )}
      </section>

      {error && <div className="error">{error}</div>}
      <section className="discovery-map-section">
        <div className="map-section-heading">
          <div><span className="kicker">Explore the area</span><strong>Shops around your pickup point</strong></div>
          <span className="map-key"><i /> Your location · <b /> Selected shop</span>
        </div>
        <MapView center={coords} stores={markers} selectedId={selected} onSelect={setSelected}
          onOpen={(merchantId) => navigate(`/store/${merchantId}`)} height={420} />
      </section>

      <section className="discover-results-section">
        <div className="map-section-heading">
          <div><span className="kicker">{searching ? 'Search matches' : 'Ready for pickup'}</span><strong>{loading ? 'Refreshing nearby shops…' : `${visibleCount} ${searching ? 'matches' : 'places to explore'}`}</strong></div>
          {!loading && visibleCount > visibleLimit && <span className="muted small">Showing {Math.min(visibleLimit, visibleCount)} of {visibleCount}</span>}
        </div>
        {loading && <div className="discover-loading">Finding the closest options…</div>}
        {searching ? (
          <SearchResults results={visibleResults} allCount={results.length} loading={loading}
            onOpen={(id) => navigate(`/store/${id}`)} selected={selected} onSelect={setSelected}
            onMore={() => setVisibleLimit((limit) => limit + 12)} />
        ) : (
          <StoreResults stores={visibleStores} allCount={stores.length} loading={loading} radiusKm={radiusKm}
            onOpen={(id) => navigate(`/store/${id}`)} selected={selected} onSelect={setSelected}
            onMore={() => setVisibleLimit((limit) => limit + 12)} />
        )}
      </section>
    </main>
  );
}

function StoreResults({ stores, allCount, loading, radiusKm, onOpen, selected, onSelect, onMore }: {
  stores: StoreDiscovery[]; allCount: number; loading: boolean; radiusKm: number;
  onOpen: (id: number) => void; selected: number | null; onSelect: (id: number) => void; onMore: () => void;
}) {
  if (!loading && stores.length === 0) {
    return <div className="card muted">No shops within {radiusKm} km. Try a wider radius.</div>;
  }
  return (<>
    <div className="store-results-grid">
      {stores.map((s) => (
        <div key={s.merchantId}
          className={`store-result-card selectable ${selected === s.merchantId ? 'selected' : ''}`}
          onClick={() => onSelect(s.merchantId)}>
          <div className="spread">
            <strong>{s.storeName}</strong>
            <span className="badge steel">{s.storeType.toLowerCase().replace('_', ' ')}</span>
          </div>
          <span className="muted small">{s.address}</span>
          <div className="row wrap">
            <span className="badge info">{s.distanceKm} km</span>
            <span className="badge">{s.travelMins} min away</span>
            {s.prepTimeMins != null && <span className="badge warn">~{s.prepTimeMins} min prep</span>}
          </div>
          <button className="primary" onClick={() => onOpen(s.merchantId)}>Open menu</button>
        </div>
      ))}
    </div>
    {stores.length < allCount && <button className="show-more" onClick={onMore}>Show more places</button>}
  </>);
}

function SearchResults({ results, allCount, loading, onOpen, selected, onSelect, onMore }: {
  results: SearchResult[]; allCount: number; loading: boolean;
  onOpen: (id: number) => void; selected: number | null; onSelect: (id: number) => void; onMore: () => void;
}) {
  if (!loading && results.length === 0) {
    return <div className="card muted">No items found. Try a different term or a wider radius.</div>;
  }
  return (<>
    <div className="store-results-grid">
      {results.map((r) => (
        <div key={r.menuItemId}
          className={`store-result-card selectable ${selected === r.merchantId ? 'selected' : ''}`}
          onClick={() => onSelect(r.merchantId)}>
          <div className="spread">
            <strong>{r.itemName}</strong>
            <span className="price">₹{r.price.toFixed(0)}</span>
          </div>
          <span className="muted small">
            {r.storeName} · <span className="badge steel">{r.storeType.toLowerCase().replace('_', ' ')}</span>
          </span>
          <div className="row wrap">
            <span className="badge info">{r.distanceKm} km</span>
            <span className="badge">{r.travelMins} min away</span>
          </div>
          <button className="primary" onClick={() => onOpen(r.merchantId)}>Open shop</button>
        </div>
      ))}
    </div>
    {results.length < allCount && <button className="show-more" onClick={onMore}>Show more matches</button>}
  </>);
}

/** Collapses item search results to unique shops for the map markers. */
function dedupeShops(results: SearchResult[]): StoreDiscovery[] {
  const byShop = new Map<number, StoreDiscovery>();
  for (const r of results) {
    if (!byShop.has(r.merchantId)) {
      byShop.set(r.merchantId, {
        merchantId: r.merchantId, storeName: r.storeName, storeType: r.storeType,
        address: r.address, latitude: r.latitude, longitude: r.longitude,
        distanceKm: r.distanceKm, travelMins: r.travelMins, prepTimeMins: null,
      });
    }
  }
  return [...byShop.values()];
}
