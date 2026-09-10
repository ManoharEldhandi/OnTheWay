import type { EtaQuote } from '../types';

function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/** Shows the inputs and decisions behind the ETA instead of presenting a black-box estimate. */
export function EtaExplainer({ eta, live = false, preparingNow = false }: { eta: EtaQuote; live?: boolean; preparingNow?: boolean }) {
  const startsNow = preparingNow || new Date(eta.prepStartAt).getTime() <= Date.now() + 60_000;

  return (
    <section className="eta-explainer" aria-label="How pickup timing is calculated">
      <div className="spread eta-explainer-head">
        <div><span className="kicker">Transparent ETA</span><strong>How this pickup time is calculated</strong></div>
        <span className="badge info">{live ? 'recomputed live' : 'preview'}</span>
      </div>
      <div className="eta-flow">
        <div className="eta-step"><span className="eta-step-num">1</span><div><strong>{eta.distanceKm} km route</strong><small>The route model estimates {eta.travelMins} min travel.</small></div></div>
        <span className="eta-arrow" aria-hidden="true">→</span>
        <div className="eta-step"><span className="eta-step-num">2</span><div><strong>±{eta.trafficBufferMins} min traffic</strong><small>Arrival window: {clockTime(eta.etaEarliest)}–{clockTime(eta.etaLatest)}.</small></div></div>
        <span className="eta-arrow" aria-hidden="true">→</span>
        <div className="eta-step"><span className="eta-step-num">3</span><div><strong>{eta.prepTimeMins} + {eta.bufferMins} min prep</strong><small>{preparingNow ? 'Preparation is underway.' : `Kitchen ${startsNow ? 'starts now' : `starts at ${clockTime(eta.prepStartAt)}`}.`}</small></div></div>
        <span className="eta-arrow" aria-hidden="true">→</span>
        <div className="eta-step ready"><span className="eta-step-num">4</span><div><strong>Ready {clockTime(eta.readyAt)}</strong><small>Fresh for the expected arrival.</small></div></div>
      </div>
      {live && <p className="eta-explainer-note">Each location update recalculates this chain and sends the new ETA to the merchant tab.</p>}
    </section>
  );
}
