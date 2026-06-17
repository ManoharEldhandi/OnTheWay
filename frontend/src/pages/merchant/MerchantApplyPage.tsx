import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import type { Shop, StoreType } from '../../types';

const VERTICALS: StoreType[] = [
  'RESTAURANT', 'FAST_FOOD', 'CAFE', 'BAKERY', 'PHARMACY', 'MEDICAL', 'GROCERY',
  'SUPERMARKET', 'HOTEL', 'BOOKSTORE', 'ELECTRONICS', 'HARDWARE', 'FLORIST', 'PET_STORE', 'RETAIL', 'OTHER',
];

export function MerchantApplyPage() {
  const navigate = useNavigate();
  const [storeName, setStoreName] = useState('');
  const [storeType, setStoreType] = useState<StoreType>('RESTAURANT');
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState('12.9716');
  const [longitude, setLongitude] = useState('77.5946');
  const [prepTimeMins, setPrepTimeMins] = useState('15');
  const [etaBufferMins, setEtaBufferMins] = useState('5');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.post<Shop>('/api/merchant/shops', {
        storeName,
        storeType,
        address,
        latitude: Number(latitude),
        longitude: Number(longitude),
        prepTimeMins: Number(prepTimeMins),
        etaBufferMins: Number(etaBufferMins),
      });
      navigate('/merchant');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit the application');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="col">
      <div>
        <h1 className="title">Open a shop</h1>
        <p className="sub">Submit your shop for review. Once an administrator approves it, it becomes discoverable.</p>
      </div>

      <form className="card col" onSubmit={submit} style={{ maxWidth: 640 }}>
        <div className="col">
          <label>Shop name</label>
          <input value={storeName} onChange={(e) => setStoreName(e.target.value)} required />
        </div>
        <div className="filters-grid">
          <div className="col">
            <label>Vertical</label>
            <select value={storeType} onChange={(e) => setStoreType(e.target.value as StoreType)}>
              {VERTICALS.map((v) => (
                <option key={v} value={v}>{v.charAt(0) + v.slice(1).toLowerCase().replace('_', ' ')}</option>
              ))}
            </select>
          </div>
          <div className="col">
            <label>Address</label>
            <input value={address} onChange={(e) => setAddress(e.target.value)} required />
          </div>
        </div>
        <div className="filters-grid">
          <div className="col">
            <label>Latitude</label>
            <input value={latitude} onChange={(e) => setLatitude(e.target.value)} required />
          </div>
          <div className="col">
            <label>Longitude</label>
            <input value={longitude} onChange={(e) => setLongitude(e.target.value)} required />
          </div>
        </div>
        <div className="filters-grid">
          <div className="col">
            <label>Preparation time (minutes)</label>
            <input type="number" min={0} value={prepTimeMins} onChange={(e) => setPrepTimeMins(e.target.value)} />
          </div>
          <div className="col">
            <label>ETA buffer (minutes)</label>
            <input type="number" min={1} value={etaBufferMins} onChange={(e) => setEtaBufferMins(e.target.value)} />
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        <div className="row">
          <button className="ghost" type="button" onClick={() => navigate('/merchant')}>Cancel</button>
          <button className="primary grow" type="submit" disabled={busy}>
            {busy ? 'Submitting…' : 'Submit application'}
          </button>
        </div>
      </form>
    </div>
  );
}
