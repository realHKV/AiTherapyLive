import React, { useCallback, useEffect, useState } from 'react';
import { profileAPI } from '../api/profile';

const PAGE_SIZE = 20;
const TYPES = ['life_event', 'goal', 'relationship', 'preference', 'fact'];
const label = value => value.replaceAll('_', ' ');

export default function MemoryReview({ isOpen, refreshKey }) {
  const [candidates, setCandidates] = useState([]);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [memories, setMemories] = useState([]);
  const [memoryTotal, setMemoryTotal] = useState(0);
  const [drafts, setDrafts] = useState({});
  const [newMemory, setNewMemory] = useState({ type: 'fact', title: '', detail: '' });
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [pending, saved] = await Promise.all([
        profileAPI.getCandidates({ offset: 0, limit: PAGE_SIZE }),
        profileAPI.getMemories({ offset: 0, limit: PAGE_SIZE }),
      ]);
      setCandidates(pending.memories || []);
      setCandidateTotal(pending.total || 0);
      setMemories(saved.memories || []);
      setMemoryTotal(saved.total || 0);
      setDrafts({});
      setError('');
    } catch {
      setError('Memories could not be loaded. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { if (isOpen) reload(); }, [isOpen, refreshKey, reload]);

  const loadMore = async (kind) => {
    setBusy(kind);
    try {
      const current = kind === 'candidates' ? candidates : memories;
      const page = await (kind === 'candidates' ? profileAPI.getCandidates : profileAPI.getMemories)(
        { offset: current.length, limit: PAGE_SIZE });
      if (kind === 'candidates') {
        setCandidates(previous => [...previous, ...(page.memories || [])]);
        setCandidateTotal(page.total);
      } else {
        setMemories(previous => [...previous, ...(page.memories || [])]);
        setMemoryTotal(page.total);
      }
    } catch {
      setError('More memories could not be loaded. Please try again.');
    } finally {
      setBusy('');
    }
  };

  const act = async (id, operation) => {
    setBusy(id);
    setError('');
    try {
      await operation();
      await reload();
      return true;
    } catch (cause) {
      if (cause.response?.status === 412 || cause.response?.status === 428) {
        await reload();
        setError('This memory changed while you were editing. Review the latest version before trying again.');
      } else {
        setError(cause.response?.data?.error?.message || 'The change could not be saved. Please try again.');
      }
      return false;
    } finally {
      setBusy('');
    }
  };

  const setDraft = (id, field, value) => setDrafts(previous => ({
    ...previous, [id]: { ...previous[id], [field]: value },
  }));

  const fields = item => ({
    title: drafts[item.id]?.title ?? item.title,
    detail: drafts[item.id]?.detail ?? item.detail ?? '',
  });

  const changedFields = item => {
    const current = fields(item);
    return {
      ...(current.title.trim() !== item.title ? { title: current.title.trim() } : {}),
      ...(current.detail !== (item.detail ?? '') ? { detail: current.detail } : {}),
    };
  };

  const editFields = item => (
    <div className="space-y-2">
      <input aria-label="Memory title" maxLength={200} value={fields(item).title}
        onChange={event => setDraft(item.id, 'title', event.target.value)}
        className="w-full rounded-lg bg-surface-container-high px-3 py-2 text-sm text-on-surface" />
      <textarea aria-label="Memory detail" maxLength={2000} rows={2} value={fields(item).detail}
        onChange={event => setDraft(item.id, 'detail', event.target.value)}
        className="w-full rounded-lg bg-surface-container-high px-3 py-2 text-sm text-on-surface" />
    </div>
  );

  const groups = Object.entries(candidates.reduce((result, candidate) => {
    (result[candidate.type] ||= []).push(candidate);
    return result;
  }, {}));

  return (
    <section className="space-y-5">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50">Memory review</h3>
        <button onClick={reload} disabled={loading || !!busy} className="text-xs text-primary hover:underline disabled:opacity-50">Refresh</button>
      </div>
      <p className="text-xs text-on-surface-variant">Suggested memories stay out of AI context until you confirm them.</p>
      {error && <p role="alert" className="text-xs text-error">{error}</p>}
      {loading && <p className="text-xs text-on-surface-variant">Loading memories...</p>}

      <div className="space-y-3">
        <h4 className="text-sm font-semibold text-on-surface">Suggestions ({candidateTotal})</h4>
        {!loading && candidates.length === 0 && <p className="text-xs text-on-surface-variant">No suggestions to review.</p>}
        {groups.map(([type, items]) => (
          <div key={type} className="space-y-2">
            <p className="text-xs font-semibold capitalize text-primary">{label(type)}</p>
            {items.map(item => (
              <div key={item.id} className="rounded-xl border border-outline-variant/20 bg-surface-container-low p-3 space-y-2">
                {editFields(item)}
                {item.sourceKind && <p className="text-xs text-on-surface-variant">Source: {label(item.sourceKind)}</p>}
                <div className="flex gap-2">
                  <button disabled={!!busy || !fields(item).title.trim()}
                    onClick={() => act(item.id, () => profileAPI.confirmCandidate(item.id, item.version, changedFields(item)))}
                    className="rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-on-primary disabled:opacity-50">Confirm</button>
                  <button disabled={!!busy} onClick={() => act(item.id, () => profileAPI.dismissCandidate(item.id, item.version))}
                    className="rounded-lg border border-outline-variant/30 px-3 py-1.5 text-xs text-on-surface disabled:opacity-50">Dismiss</button>
                </div>
              </div>
            ))}
          </div>
        ))}
        {candidates.length < candidateTotal && <button disabled={!!busy} onClick={() => loadMore('candidates')}
          className="text-xs text-primary hover:underline disabled:opacity-50">Load more suggestions</button>}
      </div>

      <div className="space-y-3">
        <h4 className="text-sm font-semibold text-on-surface">Saved memories ({memoryTotal})</h4>
        {!loading && memories.length === 0 && <p className="text-xs text-on-surface-variant">No saved memories yet.</p>}
        {memories.map(item => (
          <div key={item.id} className="rounded-xl border border-outline-variant/20 bg-surface-container-low p-3 space-y-2">
            <p className="text-xs capitalize text-on-surface-variant">{label(item.type)} · {item.status}</p>
            {editFields(item)}
            <div className="flex flex-wrap gap-2">
              <button disabled={!!busy || !fields(item).title.trim() || Object.keys(changedFields(item)).length === 0}
                onClick={() => act(item.id, () => profileAPI.editMemory(item.id, item.version, changedFields(item)))}
                className="rounded-lg border border-primary/40 px-3 py-1.5 text-xs text-primary disabled:opacity-50">Save edits</button>
              <button disabled={!!busy} onClick={() => act(item.id, () => profileAPI.editMemory(item.id, item.version,
                { status: item.status === 'resolved' ? 'active' : 'resolved' }))}
                className="rounded-lg border border-outline-variant/30 px-3 py-1.5 text-xs text-on-surface disabled:opacity-50">
                {item.status === 'resolved' ? 'Reopen' : 'Resolve'}
              </button>
              <button disabled={!!busy} onClick={() => {
                if (window.confirm('Forget this memory and exclude its known source from future AI context?'))
                  act(item.id, () => profileAPI.forgetMemory(item.id, item.version));
              }} className="rounded-lg border border-error/30 px-3 py-1.5 text-xs text-error disabled:opacity-50">Forget</button>
            </div>
          </div>
        ))}
        {memories.length < memoryTotal && <button disabled={!!busy} onClick={() => loadMore('memories')}
          className="text-xs text-primary hover:underline disabled:opacity-50">Load more saved memories</button>}
      </div>

      <form onSubmit={event => {
        event.preventDefault();
        act('new', () => profileAPI.addMemory({ ...newMemory, title: newMemory.title.trim(), importance: 5 }))
          .then(saved => { if (saved) setNewMemory({ type: 'fact', title: '', detail: '' }); });
      }} className="space-y-2 border-t border-outline-variant/20 pt-4">
        <h4 className="text-sm font-semibold text-on-surface">Save a memory yourself</h4>
        <select aria-label="Memory type" value={newMemory.type} onChange={event => setNewMemory({ ...newMemory, type: event.target.value })}
          className="w-full rounded-lg bg-surface-container-high px-3 py-2 text-sm text-on-surface">
          {TYPES.map(type => <option key={type} value={type}>{label(type)}</option>)}
        </select>
        <input aria-label="New memory title" placeholder="Title" maxLength={200} value={newMemory.title}
          onChange={event => setNewMemory({ ...newMemory, title: event.target.value })}
          className="w-full rounded-lg bg-surface-container-high px-3 py-2 text-sm text-on-surface" />
        <textarea aria-label="New memory detail" placeholder="Optional detail" maxLength={2000} rows={2} value={newMemory.detail}
          onChange={event => setNewMemory({ ...newMemory, detail: event.target.value })}
          className="w-full rounded-lg bg-surface-container-high px-3 py-2 text-sm text-on-surface" />
        <button type="submit" disabled={!!busy || !newMemory.title.trim()}
          className="rounded-lg bg-primary px-3 py-2 text-xs font-semibold text-on-primary disabled:opacity-50">Save memory</button>
      </form>
    </section>
  );
}
