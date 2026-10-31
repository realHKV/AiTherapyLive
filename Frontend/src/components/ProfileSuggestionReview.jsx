import React, { useCallback, useEffect, useState } from 'react';
import { profileAPI } from '../api/profile';

const fieldLabel = field => field.startsWith('trait:')
  ? `Trait: ${field.slice('trait:'.length)}`
  : ({ name: 'Preferred name', age_range: 'Age range', gender: 'Gender' }[field] || field);

export default function ProfileSuggestionReview({ isOpen, refreshKey, onProfileChanged }) {
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState('');

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setSuggestions(await profileAPI.getProfileCandidates());
      setError('');
    } catch {
      setError('Profile suggestions could not be loaded. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { if (isOpen) reload(); }, [isOpen, refreshKey, reload]);

  const act = async (item, confirm) => {
    setBusy(item.id);
    setError('');
    try {
      if (confirm) {
        const updated = await profileAPI.confirmProfileCandidate(item.id);
        onProfileChanged(updated);
      } else {
        await profileAPI.dismissProfileCandidate(item.id);
      }
      await reload();
    } catch {
      setError('That suggestion could not be saved. Refresh and try again.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50">Profile suggestions</h3>
        <button onClick={reload} disabled={loading || !!busy} className="min-h-11 inline-flex items-center text-xs text-primary hover:underline disabled:opacity-50">Refresh</button>
      </div>
      <p className="text-xs text-on-surface-variant">Insights from chat appear here after you update your profile. Review each suggestion before adding it to your profile.</p>
      {error && <p role="alert" className="text-xs text-error">{error}</p>}
      {loading && <p className="text-xs text-on-surface-variant">Loading suggestions...</p>}
      {!loading && suggestions.length === 0 && <p className="text-xs text-on-surface-variant">No profile suggestions to review.</p>}
      {suggestions.map(item => (
        <div key={item.id} className="rounded-xl border border-outline-variant/20 bg-surface-container-low p-3 space-y-2">
          <p className="text-xs font-semibold text-primary">{fieldLabel(item.field)}</p>
          <p className="text-sm text-on-surface">{item.proposedValue}</p>
          <p className="text-xs text-on-surface-variant">From your message: “{item.evidence}”</p>
          <div className="flex flex-wrap gap-2">
            <button disabled={!!busy} onClick={() => act(item, true)}
              className="min-h-11 rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-on-primary disabled:opacity-50">Apply</button>
            <button disabled={!!busy} onClick={() => act(item, false)}
              className="min-h-11 rounded-lg border border-outline-variant/30 px-3 py-1.5 text-xs text-on-surface disabled:opacity-50">Dismiss</button>
          </div>
        </div>
      ))}
    </section>
  );
}
