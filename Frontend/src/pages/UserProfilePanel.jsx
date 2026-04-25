import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useSubscription } from '../context/SubscriptionContext';
import { profileAPI } from '../api/profile';

export default function UserProfilePanel({ isOpen, onClose, profile, memories, onProfileDeleted, enable3DBackground, onToggle3DBackground }) {
  const { deleteAccount } = useAuth();
  const { isPro, openUpgradeLink, tier, proExpiresAt, isDevMode, devForcePro, devForceFree } = useSubscription();
  const [isDeletingProfile, setIsDeletingProfile] = useState(false);
  const [isDeletingAccount, setIsDeletingAccount] = useState(false);
  const [devLoading, setDevLoading] = useState(false);

  const handleDeleteProfile = async () => {
    if (window.confirm("Are you sure you want to delete your AI profile? Your account will remain, but the AI will forget all personalized data and memories.")) {
      setIsDeletingProfile(true);
      try {
        await profileAPI.deleteProfile();
        alert('Profile deleted successfully.');
        if (onProfileDeleted) onProfileDeleted();
        onClose();
      } catch (e) {
        console.error('Failed to delete profile', e);
        alert('Failed to delete profile.');
      } finally {
        setIsDeletingProfile(false);
      }
    }
  };

  const handleDeleteAccount = async () => {
    if (window.confirm("WARNING: Are you entirely sure you want to PERMANENTLY delete your account? All messages, memories, and data will be erased immediately. This cannot be undone.")) {
      setIsDeletingAccount(true);
      try {
        await deleteAccount();
      } catch (e) {
        alert('Failed to delete account.');
        setIsDeletingAccount(false);
      }
    }
  };

  if (!isOpen) return null;

  return (
    /* Overlay */
    <div
      className="fixed inset-0 z-[100] flex items-start justify-end"
      style={{ background: 'rgba(6, 14, 32, 0.8)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      {/* Panel */}
      <div
        onClick={e => e.stopPropagation()}
        className="relative h-full w-full max-w-md overflow-y-auto custom-scrollbar"
        style={{
          background: 'rgba(9, 19, 40, 0.95)',
          backdropFilter: 'blur(24px)',
          WebkitBackdropFilter: 'blur(24px)',
          borderLeft: '1px solid rgba(64, 72, 93, 0.3)',
        }}
      >
        {/* Header */}
        <div className="sticky top-0 z-10 px-6 py-5 flex items-center justify-between"
          style={{ background: 'rgba(9, 19, 40, 0.95)', borderBottom: '1px solid rgba(64, 72, 93, 0.15)' }}
        >
          <div>
            <h2 className="text-xl font-bold font-headline text-on-surface flex items-center gap-2">
              Patient Profile
              {isPro && (
                <span className="px-2 py-0.5 rounded-full bg-primary/20 text-primary text-[10px] font-bold border border-primary/30">✨ PRO</span>
              )}
            </h2>
            <p className="text-xs text-on-surface-variant/60 mt-0.5">AI-generated insights from your sessions</p>
          </div>
          <button
            onClick={onClose}
            className="w-9 h-9 flex items-center justify-center rounded-full bg-surface-container-high hover:bg-surface-bright text-on-surface-variant hover:text-on-surface transition-all text-lg"
          >
            ×
          </button>
        </div>

        <div className="px-6 py-6 space-y-6">

          {/* Identity Section */}
          <section>
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50 mb-4 flex items-center gap-2">
              <span>Identity</span>
            </h3>
            <div className="space-y-3">
              {[
                { label: 'Name', value: profile?.preferredName || profile?.email || 'N/A' },
                { label: 'Age Range', value: profile?.ageRange || 'Unknown' },
                { label: 'Communication Style', value: profile?.communicationStyle || 'Default' },
                { label: 'AI Persona', value: profile?.aiPersona || 'Standard' },
              ].map(item => (
                <div key={item.label} className="flex items-center justify-between py-3 border-b border-outline-variant/10 last:border-0">
                  <span className="text-sm text-on-surface-variant">{item.label}</span>
                  <span className="text-sm font-semibold text-on-surface">{item.value}</span>
                </div>
              ))}
            </div>
          </section>

          {/* Topics of Concern */}
          <section>
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50 mb-4">
              Topics of Concern
            </h3>
            <div className="flex flex-wrap gap-2">
              {profile?.topicsOfConcern?.length ? (
                profile.topicsOfConcern.map((topic, i) => (
                  <span
                    key={i}
                    className="px-3 py-1.5 rounded-full text-xs font-semibold bg-primary/10 text-primary border border-primary/20"
                  >
                    {topic}
                  </span>
                ))
              ) : (
                <span className="text-sm text-on-surface-variant/50 italic">No topics identified yet.</span>
              )}
            </div>
          </section>

          {/* AI-Generated Traits */}
          <section>
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50 mb-4">
              Generated Traits
            </h3>
            <div className="space-y-4">
              {profile?.traits?.length ? (
                profile.traits.map((trait, i) => (
                  <div key={i} className="p-4 rounded-xl bg-surface-container-high">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-semibold text-on-surface">{trait.key}</span>
                      <span className="text-xs font-bold text-secondary">{Math.round(trait.confidence * 100)}%</span>
                    </div>
                    <div className="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-700"
                        style={{
                          width: `${Math.max(5, trait.confidence * 100)}%`,
                          background: 'linear-gradient(90deg, #cc97ff, #53ddfc)',
                        }}
                      />
                    </div>
                    <p className="text-[10px] text-on-surface-variant/40 mt-1.5 uppercase tracking-wider">
                      Source: {trait.source}
                    </p>
                  </div>
                ))
              ) : (
                <span className="text-sm text-on-surface-variant/50 italic">No traits evaluated yet.</span>
              )}
            </div>
          </section>

          {/* Recent Memories */}
          <section>
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50 mb-4">
              Recent Memories <span className="text-tertiary ml-1">🔒 Encrypted</span>
            </h3>
            <div className="space-y-3">
              {memories?.length ? (
                memories.map((mem) => (
                  <div key={mem.id} className="p-4 rounded-xl bg-surface-container-high border border-outline-variant/10">
                    <div className="flex items-start justify-between mb-1.5">
                      <span className="text-sm font-bold text-on-surface">{mem.title}</span>
                      <span className="text-secondary text-xs">
                        {'★'.repeat(mem.importance || 1)}
                      </span>
                    </div>
                    <p className="text-sm text-on-surface-variant leading-relaxed">{mem.detail}</p>
                    {mem.occurredAt && (
                      <p className="text-[10px] text-on-surface-variant/40 mt-2 uppercase tracking-wider">
                        {mem.occurredAt}
                      </p>
                    )}
                  </div>
                ))
              ) : (
                <span className="text-sm text-on-surface-variant/50 italic">No memories extracted yet.</span>
              )}
            </div>
          </section>

          {/* Preferences */}
          <section>
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50 mb-4">
              Preferences
            </h3>
            <div className="flex items-center justify-between p-4 rounded-xl bg-surface-container-high border border-outline-variant/10">
              <div>
                <p className="text-sm font-semibold text-on-surface">Immersive Background</p>
                <p className="text-xs text-on-surface-variant mt-0.5">Show 3D galaxy animation in chat</p>
              </div>
              <button
                onClick={onToggle3DBackground}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${enable3DBackground ? 'bg-primary' : 'bg-surface-container-highest border border-outline-variant/30'}`}
              >
                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${enable3DBackground ? 'translate-x-6' : 'translate-x-1'}`} />
              </button>
            </div>
          </section>

          {/* Subscription Section */}
          <section className={`rounded-xl p-5 border ${isPro ? 'border-primary/30 bg-primary/5' : 'border-outline-variant/20 bg-surface-container-low'}`}>
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant/50 mb-3">Subscription</h3>
            {isPro ? (
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-bold text-primary">✨ Pro Plan</p>
                  <p className="text-xs text-on-surface-variant/60 mt-0.5">
                    {proExpiresAt
                      ? `Renews ${new Date(proExpiresAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}`
                      : 'Active'}
                  </p>
                </div>
                <span className="px-3 py-1 rounded-full bg-primary/15 text-primary text-xs font-bold border border-primary/30">Active</span>
              </div>
            ) : (
              <div>
                <p className="text-sm text-on-surface-variant mb-3">You're on the <strong>Free</strong> plan.</p>
                <button
                  onClick={openUpgradeLink}
                  className="w-full py-2.5 rounded-full liquid-gradient text-on-primary font-headline font-bold text-sm shadow-md shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all"
                >
                  Upgrade to Pro — ₹50/mo
                </button>
                <p className="text-center text-[10px] text-on-surface-variant/40 mt-2">Secure · Razorpay · Cancel anytime</p>
              </div>
            )}
          </section>

          {/* Dev Toolbar — localhost only */}
          {isDevMode && (
            <section className="rounded-xl border border-orange-500/40 bg-orange-500/5 p-5">
              <h3 className="text-xs font-bold uppercase tracking-widest text-orange-400 mb-3 flex items-center gap-2">
                <span>🛠</span> Dev Tools
                <span className="ml-auto px-2 py-0.5 rounded-full bg-orange-500/20 text-orange-300 text-[10px] font-bold">localhost only</span>
              </h3>
              <p className="text-xs text-orange-300/60 mb-4">
                Current tier: <strong className="text-orange-300">{tier}</strong>
                {isPro && proExpiresAt && (
                  <span className="ml-2 text-orange-300/40">· expires {new Date(proExpiresAt).toLocaleDateString()}</span>
                )}
              </p>
              <div className="flex gap-3">
                <button
                  onClick={async () => {
                    setDevLoading(true);
                    try {
                      await devForcePro();
                    } catch (err) {
                      console.error('[DEV] Force PRO failed:', err);
                      alert('Force PRO failed: ' + (err?.response?.data?.error?.message || err?.message || 'Unknown error. Check backend console.'));
                    } finally {
                      setDevLoading(false);
                    }
                  }}
                  disabled={devLoading || isPro}
                  className="flex-1 py-2 rounded-lg bg-orange-500/20 border border-orange-500/30 text-orange-300 text-xs font-bold hover:bg-orange-500/30 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {devLoading ? '⏳ ...' : '⚡ Force PRO'}
                </button>
                <button
                  onClick={async () => {
                    setDevLoading(true);
                    try {
                      await devForceFree();
                    } catch (err) {
                      console.error('[DEV] Force FREE failed:', err);
                      alert('Force FREE failed: ' + (err?.response?.data?.error?.message || err?.message || 'Unknown error.'));
                    } finally {
                      setDevLoading(false);
                    }
                  }}
                  disabled={devLoading || !isPro}
                  className="flex-1 py-2 rounded-lg bg-surface-container border border-outline-variant/20 text-on-surface-variant text-xs font-bold hover:bg-surface-container-high transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {devLoading ? '⏳ ...' : '↩ Revert FREE'}
                </button>
              </div>
            </section>
          )}

          {/* Danger Zone */}
          <section className="border border-error/20 rounded-xl p-5 bg-error/5">
            <h3 className="text-xs font-bold uppercase tracking-widest text-error/70 mb-4 flex items-center gap-2">
              ⚠️ Danger Zone
            </h3>
            <div className="space-y-3">
              <button
                onClick={handleDeleteProfile}
                disabled={isDeletingProfile || isDeletingAccount}
                className="w-full py-3 px-4 rounded-xl bg-error/10 border border-error/20 text-error text-sm font-semibold hover:bg-error/20 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                title="Delete AI Profile & Memories only. Account remains active."
              >
                {isDeletingProfile ? 'Deleting Profile...' : 'Delete AI Profile'}
              </button>
              <button
                onClick={handleDeleteAccount}
                disabled={isDeletingProfile || isDeletingAccount}
                className="w-full py-3 px-4 rounded-xl bg-error/20 border border-error/40 text-error text-sm font-bold hover:bg-error/30 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                title="Permanently delete your account and ALL associated data."
              >
                {isDeletingAccount ? 'Deleting Account...' : '🗑️ Delete Account Permanently'}
              </button>
              <p className="text-xs text-error/50 leading-relaxed">
                Account deletion is irreversible and removes all messages, memories, and session data.
              </p>
            </div>
          </section>

        </div>
      </div>
    </div>
  );
}
