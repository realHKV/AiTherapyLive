import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { profileAPI } from '../api/profile';

const RAZORPAY_LINK = 'https://rzp.io/rzp/jEBZVu6s';

const THERAPISTS = [
  {
    id: 'dr_sarah', // ID kept for database consistency
    name: 'Sarah',
    title: 'Warm & Empathetic AI Persona',
    initials: 'S',
    gradientFrom: 'rgba(204, 151, 255, 0.4)',
    gradientTo: 'rgba(204, 151, 255, 0.15)',
    accentColor: 'primary',
    accentHex: '#cc97ff',
    accentBg: 'bg-[#cc97ff]/10',
    accentText: 'text-[#cc97ff]',
    accentBorder: 'border-[#cc97ff]/40',
    accentShadow: 'shadow-[0_0_24px_rgba(204,151,255,0.2)]',
    specialities: ['Anxiety & Worry', 'Mindfulness', 'Life Transitions', 'Self-Compassion'],
    approach: 'Cognitive Behavioural Framework + Mindfulness',
    styleDesc: "Sarah provides a safe, judgement-free space designed to help you explore your thoughts at your own pace. She emphasizes deep listening and gentle curiosity.",
    tone: 'Gentle',
    focus: 'Emotional Exploration',
    framework: 'CBT-inspired',
    badge: { label: 'Most Chosen', color: 'text-[#cc97ff]', bg: 'bg-[#cc97ff]/10', border: 'border-[#cc97ff]/30' },
  },
  {
    id: 'dr_alex', 
    name: 'Alex',
    title: 'Direct & Action-Oriented AI Persona',
    initials: 'A',
    gradientFrom: 'rgba(83, 221, 252, 0.4)',
    gradientTo: 'rgba(83, 221, 252, 0.15)',
    accentColor: 'secondary',
    accentHex: '#53ddfc',
    accentBg: 'bg-[#53ddfc]/10',
    accentText: 'text-[#53ddfc]',
    accentBorder: 'border-[#53ddfc]/40',
    accentShadow: 'shadow-[0_0_24px_rgba(83,221,252,0.2)]',
    specialities: ['Stress & Burnout', 'Career Challenges', 'Relationships', 'Goal Setting'],
    approach: 'Solution-Focused Framework + Action Planning',
    styleDesc: "Alex cuts through the noise and focuses on actionable change. If you value clarity, structure, and measurable progress, Alex's style will feel grounding and energizing.",
    tone: 'Direct',
    focus: 'Problem-Solving',
    framework: 'SFBT-inspired',
    badge: null,
  },
];

function TherapistCard({ therapist, selected, onSelect }) {
  const isSelected = selected === therapist.id;

  return (
    <div
      onClick={() => onSelect(therapist.id)}
      className={`relative p-7 rounded-xl flex flex-col h-full cursor-pointer transition-all duration-300 border ${
        isSelected
          ? `bg-surface-container ${therapist.accentBorder} ${therapist.accentShadow} scale-[1.01]`
          : 'bg-surface-container-low border-outline-variant/10 hover:bg-surface-container hover:border-outline-variant/30'
      }`}
    >
      {/* Badge */}
      {therapist.badge && (
        <div className={`absolute top-4 right-4 px-3 py-1 rounded-full text-xs font-bold ${therapist.badge.bg} ${therapist.badge.border} border ${therapist.badge.color}`}>
          {therapist.badge.label}
        </div>
      )}

      {/* Selected check */}
      {isSelected && (
        <div className={`absolute top-4 left-4 w-6 h-6 rounded-full flex items-center justify-center text-sm font-bold text-on-primary bg-primary`}>
          ✓
        </div>
      )}

      {/* Avatar + Name */}
      <div className="flex items-start gap-5 mb-5">
        {/* Avatar */}
        <div
          className={`w-16 h-16 rounded-full flex items-center justify-center text-xl font-bold text-white shrink-0 ${therapist.accentShadow}`}
          style={{
            background: `radial-gradient(circle at 35% 35%, ${therapist.gradientFrom}, ${therapist.gradientTo})`,
            border: `2.5px solid ${therapist.accentHex}60`,
          }}
        >
          {therapist.initials}
        </div>
        <div>
          <h2 className="text-xl font-bold font-headline text-on-surface mb-1">{therapist.name}</h2>
          <p className="text-sm text-on-surface-variant mb-2">{therapist.title}</p>
        </div>
      </div>

      {/* Stats row */}
      <div className="flex gap-6 mb-5 pb-5 border-b border-outline-variant/10">
        {[
          { label: 'Tone', value: therapist.tone },
          { label: 'Focus', value: therapist.focus },
          { label: 'Framework', value: therapist.framework },
        ].map(stat => (
          <div key={stat.label}>
            <div className="text-base font-bold text-on-surface">{stat.value}</div>
            <div className="text-xs text-on-surface-variant/50 uppercase tracking-wider mt-0.5">{stat.label}</div>
          </div>
        ))}
      </div>

      {/* Style description */}
      <p className="text-sm leading-relaxed text-on-surface-variant mb-5">{therapist.styleDesc}</p>

      {/* Speciality chips */}
      <div className="flex flex-wrap gap-2 mb-4">
        {therapist.specialities.map(s => (
          <span
            key={s}
            className={`px-3 py-1 rounded-full text-xs font-semibold ${therapist.accentBg} ${therapist.accentText} border ${therapist.accentBorder}`}
          >
            {s}
          </span>
        ))}
      </div>

      {/* Approach */}
      <p className="text-xs text-on-surface-variant/50 italic mt-auto">🎓 {therapist.approach}</p>
    </div>
  );
}

export default function ChooseTherapist() {
  const navigate = useNavigate();
  const [selected, setSelected] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [showPlanStep, setShowPlanStep] = useState(false);

  const handleConfirm = async () => {
    if (!selected) return;
    setSaving(true);
    setError(null);
    try {
      await profileAPI.saveTherapist(selected);
      setShowPlanStep(true); // Show plan selection instead of navigating
    } catch (err) {
      setError('Could not save your selection. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const handleChooseFree = () => {
    navigate('/chat');
  };

  const handleChoosePro = () => {
    window.open(RAZORPAY_LINK, '_blank', 'noopener,noreferrer');
    navigate('/chat');
  };

  return (
    <div className="min-h-screen bg-background text-on-surface relative overflow-hidden dark">
      {/* Ambient blobs */}
      <div className="absolute top-[-10%] right-[-10%] w-[40%] h-[40%] bg-primary/8 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[-10%] left-[-10%] w-[35%] h-[35%] bg-secondary/8 rounded-full blur-[120px] pointer-events-none" />

      {/* Nav */}
      <header className="fixed top-0 w-full z-50 glass-morphism-nav shadow-[0_8px_32px_rgba(204,151,255,0.06)]">
        <nav className="flex justify-between items-center px-6 md:px-8 py-4 max-w-7xl mx-auto">
          <div className="text-xl font-bold tracking-tight text-primary font-headline">TrustTherapy.ai</div>
          <div className="hidden md:flex items-center gap-2 px-4 py-1.5 rounded-full bg-surface-container-highest border border-outline-variant/10 text-xs font-bold text-on-surface-variant uppercase tracking-widest">
            Step 3 of 3
          </div>
        </nav>
      </header>

      <div className="relative z-10 max-w-4xl mx-auto px-6 md:px-8 pt-32 pb-20">
        {/* Header */}
        <div className="text-center mb-12 animate-fade-up">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-surface-container-highest border border-outline-variant/10 text-xs font-bold text-on-surface-variant uppercase tracking-widest mb-5">
            Final Step · 3 of 3
          </div>
          <h1 className="text-4xl md:text-5xl font-extrabold font-headline mb-4">
            <span className="text-gradient">Choose your therapist</span>
          </h1>
          <p className="text-lg text-on-surface-variant max-w-xl mx-auto leading-relaxed">
            Both therapists are powered by the same AI — but each brings a distinct style. Choose the one that resonates with you. You can always switch later.
          </p>
        </div>

        {error && (
          <div className="mb-6 px-4 py-3 rounded-xl bg-error/10 border border-error/20 text-error text-sm font-medium max-w-xl mx-auto">
            {error}
          </div>
        )}

        {/* Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-10">
          {THERAPISTS.map((t, i) => (
            <div key={t.id} className={`animate-fade-up h-full animate-delay-${i === 0 ? '100' : '200'}`}>
              <TherapistCard therapist={t} selected={selected} onSelect={setSelected} />
            </div>
          ))}
        </div>

        {/* Confirm CTA — hidden once plan step shows */}
        {!showPlanStep && (
          <div className="flex flex-col items-center gap-4 animate-fade-up animate-delay-300">
            <button
              id="confirm-therapist-btn"
              onClick={handleConfirm}
              disabled={!selected || saving}
              className="w-full max-w-sm px-10 py-4 rounded-full bg-primary text-on-primary font-extrabold text-lg hover:scale-105 transition-all shadow-[0_10px_30px_rgba(204,151,255,0.25)] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
            >
              {saving
                ? 'Saving...'
                : selected
                ? `Start sessions with ${THERAPISTS.find(t => t.id === selected)?.name} →`
                : 'Select a therapist to continue'
              }
            </button>
            <p className="text-xs text-on-surface-variant/50">You can change your therapist anytime from settings.</p>
          </div>
        )}

        {/* Plan Selection Step — slides in after therapist confirmed */}
        {showPlanStep && (
          <div className="animate-fade-up mt-2">
            <div className="text-center mb-8">
              <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-primary/10 border border-primary/20 mb-4">
                <span className="w-2 h-2 rounded-full bg-primary animate-pulse"></span>
                <span className="text-xs font-bold uppercase tracking-widest text-primary font-label">Almost there!</span>
              </div>
              <h2 className="text-2xl font-extrabold font-headline mb-2">Choose your plan</h2>
              <p className="text-sm text-on-surface-variant">You can always upgrade later from the app.</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-6">
              {/* Free Plan */}
              <div className="rounded-2xl border border-outline-variant/20 bg-surface-container-low p-6 flex flex-col gap-5">
                <div>
                  <h3 className="font-headline text-xl font-bold mb-0.5">Free</h3>
                  <p className="text-xs text-on-surface-variant">Start your journey at no cost.</p>
                </div>
                <p className="font-headline text-3xl font-extrabold">₹0<span className="text-sm font-normal text-on-surface-variant">/mo</span></p>
                <ul className="space-y-2 text-xs text-on-surface-variant">
                  {['Unlimited sessions', 'AI memory & profile', 'Encrypted chats'].map(f => (
                    <li key={f} className="flex items-center gap-2">
                      <span className="text-primary">✓</span> {f}
                    </li>
                  ))}
                </ul>
                <button
                  onClick={handleChooseFree}
                  className="w-full py-3 rounded-full border border-outline-variant/30 font-headline font-semibold hover:bg-surface-container transition-all text-sm"
                >
                  Continue with Free
                </button>
              </div>

              {/* Pro Plan */}
              <div className="relative rounded-2xl border border-primary/40 p-6 flex flex-col gap-5 shadow-[0_0_40px_rgba(204,151,255,0.12)] overflow-hidden"
                style={{ background: 'linear-gradient(145deg, rgba(204,151,255,0.07) 0%, rgba(83,221,252,0.04) 100%)' }}>
                <div className="absolute top-4 right-4 px-2 py-0.5 rounded-full bg-primary text-on-primary text-[10px] font-bold">✨ Popular</div>
                <div className="absolute -top-8 -right-8 w-32 h-32 bg-primary/15 rounded-full blur-2xl pointer-events-none"></div>
                <div>
                  <h3 className="font-headline text-xl font-bold mb-0.5 text-primary">Pro</h3>
                  <p className="text-xs text-on-surface-variant">A smarter AI, built for you.</p>
                </div>
                <p className="font-headline text-3xl font-extrabold">₹50<span className="text-sm font-normal text-on-surface-variant">/mo</span></p>
                <ul className="space-y-2 text-xs">
                  {['Everything in Free', 'Priority AI model', 'Faster responses', 'Early features'].map(f => (
                    <li key={f} className="flex items-center gap-2 text-on-surface font-medium">
                      <span className="text-primary">✓</span> {f}
                    </li>
                  ))}
                </ul>
                <button
                  onClick={handleChoosePro}
                  className="w-full py-3 rounded-full liquid-gradient text-on-primary font-headline font-bold text-sm shadow-md shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all"
                >
                  Upgrade to Pro →
                </button>
                <p className="text-center text-[10px] text-on-surface-variant/50">Secure · Razorpay · Cancel anytime</p>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Footer */}
      <footer className="py-8 px-8 bg-surface-container-lowest border-t border-outline-variant/10">
        <div className="max-w-7xl mx-auto flex justify-between items-center">
          <div className="text-sm font-bold text-primary font-headline">TrustTherapy.ai</div>
          <p className="text-xs text-on-surface/40">© 2026 TrustTherapy.ai. Secure &amp; Encrypted.</p>
        </div>
      </footer>
    </div>
  );
}
