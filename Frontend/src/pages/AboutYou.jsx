import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { profileAPI } from '../api/profile';

const STEPS = ['Identity', 'About you', 'Concerns'];

const COUNTRIES = [
  'Afghanistan','Albania','Algeria','Argentina','Australia','Austria','Bangladesh','Belgium',
  'Brazil','Canada','Chile','China','Colombia','Croatia','Czech Republic','Denmark','Egypt',
  'Ethiopia','Finland','France','Germany','Ghana','Greece','Hungary','India','Indonesia',
  'Iran','Iraq','Ireland','Israel','Italy','Japan','Jordan','Kenya','Malaysia','Mexico',
  'Morocco','Myanmar','Nepal','Netherlands','New Zealand','Nigeria','Norway','Pakistan',
  'Peru','Philippines','Poland','Portugal','Romania','Russia','Saudi Arabia','Singapore',
  'South Africa','South Korea','Spain','Sri Lanka','Sweden','Switzerland','Taiwan','Tanzania',
  'Thailand','Turkey','Uganda','Ukraine','United Arab Emirates','United Kingdom',
  'United States','Vietnam','Zimbabwe'
];

const AGE_RANGES = ['Under 18','18–24','25–34','35–44','45–54','55–64','65+'];
const GENDERS = ['Male','Female','Non-binary','Prefer not to say'];

const CONCERNS_OPTIONS = [
  'Anxiety','Depression','Stress','Loneliness','Relationship issues',
  'Work or career pressure','Grief or loss','Self-esteem','Sleep problems',
  'Trauma','Family conflicts','Academic pressure','Life transitions','Burnout',
];

const INTEREST_OPTIONS = [
  'Music','Reading','Art / Drawing','Gaming','Sports / Fitness',
  'Cooking','Travel','Movies / TV','Writing','Nature / Outdoors',
  'Meditation / Yoga','Technology','Volunteering','Photography',
];

const CONCERN_ICONS = {
  Anxiety: '🧠', Depression: '🌧️', Stress: '⚡', Loneliness: '🌙',
  'Relationship issues': '💞', 'Work or career pressure': '💼',
  'Grief or loss': '🕊️', 'Self-esteem': '🌱', 'Sleep problems': '😴',
  Trauma: '💜', 'Family conflicts': '🏠', 'Academic pressure': '📚',
  'Life transitions': '🔄', Burnout: '🔥',
};

const INTEREST_ICONS = {
  Music: '🎵', Reading: '📖', 'Art / Drawing': '🎨', Gaming: '🎮',
  'Sports / Fitness': '🏃', Cooking: '🍳', Travel: '✈️', 'Movies / TV': '🎬',
  Writing: '✍️', 'Nature / Outdoors': '🌿', 'Meditation / Yoga': '🧘',
  Technology: '💻', Volunteering: '🤝', Photography: '📷',
};

function ProgressBar({ step }) {
  const pct = Math.round(((step) / (STEPS.length - 1)) * 100);
  return (
    <div className="w-full mb-10">
      <div className="flex justify-between items-center mb-3">
        <span className="text-xs font-bold tracking-widest text-primary uppercase">
          Step {step + 1} of {STEPS.length}
        </span>
        <span className="text-xs font-semibold text-on-surface-variant">
          {pct}% Completed
        </span>
      </div>
      <div className="h-1.5 w-full bg-surface-container-highest rounded-full overflow-hidden">
        <div
          className="h-full rounded-full shadow-[0_0_12px_rgba(83,221,252,0.4)] transition-all duration-500"
          style={{
            width: `${Math.max(5, ((step + 1) / STEPS.length) * 100)}%`,
            background: 'linear-gradient(90deg, #cc97ff, #53ddfc)',
          }}
        />
      </div>
      <div className="flex justify-between mt-3">
        {STEPS.map((label, i) => (
          <span
            key={i}
            className={`text-xs font-semibold transition-colors ${
              i === step ? 'text-primary' : i < step ? 'text-tertiary' : 'text-on-surface-variant/40'
            }`}
          >
            {i < step ? '✓ ' : ''}{label}
          </span>
        ))}
      </div>
    </div>
  );
}

function ChipSelect({ options, selected, onToggle, icons = {} }) {
  return (
    <div className="flex flex-wrap gap-2.5 mt-2">
      {options.map((opt) => {
        const isSelected = selected.includes(opt);
        return (
          <button
            key={opt}
            type="button"
            onClick={() => onToggle(opt)}
            className={`px-4 py-2 rounded-full text-sm font-semibold transition-all flex items-center gap-1.5 ${
              isSelected
                ? 'bg-secondary text-on-secondary border border-secondary shadow-[0_0_15px_rgba(83,221,252,0.3)]'
                : 'bg-surface-container-high text-on-surface-variant border border-outline-variant/20 hover:border-secondary/50 hover:text-on-surface'
            }`}
          >
            {icons[opt] && <span className="text-sm">{icons[opt]}</span>}
            {opt}
          </button>
        );
      })}
    </div>
  );
}

export default function AboutYou() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const [form, setForm] = useState({
    preferredName: '',
    ageRange: '',
    gender: '',
    country: '',
    interests: [],
    concerns: [],
    communicationStyle: '',
    additionalNote: '',
  });

  const set = (key, val) => setForm(prev => ({ ...prev, [key]: val }));
  const toggleChip = (key, val) => setForm(prev => ({
    ...prev,
    [key]: prev[key].includes(val)
      ? prev[key].filter(x => x !== val)
      : [...prev[key], val],
  }));

  const canNext = () => {
    if (step === 0) return form.preferredName.trim() && form.ageRange && form.gender && form.country;
    return true;
  };

  const handleNext = () => {
    setError(null);
    if (step === 0 && !canNext()) {
      setError('Please fill in all required fields before continuing.');
      return;
    }
    setStep(s => s + 1);
  };

  const handleBack = () => { setError(null); setStep(s => s - 1); };

  const handleSubmit = async () => {
    setSaving(true);
    setError(null);
    try {
      await profileAPI.updateProfile({
        preferredName: form.preferredName.trim(),
        ageRange: form.ageRange,
        gender: form.gender,
        country: form.country,
        topics_of_concern: JSON.stringify(form.concerns),
        enjoyments: JSON.stringify(form.interests),
        communicationStyle: form.communicationStyle || 'gentle',
        additionalNote: form.additionalNote,
      });
      navigate('/choose-therapist');
    } catch (err) {
      setError('Failed to save your profile. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const inputClass = "w-full bg-surface-container-high border border-outline-variant/20 rounded-lg px-4 py-3.5 text-on-surface placeholder:text-on-surface-variant/40 focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary/40 transition-all text-sm font-medium";
  const labelClass = "block text-sm font-semibold text-primary/80 mb-2";

  return (
    <div className="min-h-screen bg-background text-on-surface relative overflow-hidden dark">
      {/* Ambient blobs */}
      <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/8 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-secondary/8 rounded-full blur-[120px] pointer-events-none" />

      {/* ── Nav ── */}
      <header className="fixed top-0 w-full z-50 glass-morphism-nav shadow-[0_8px_32px_rgba(204,151,255,0.06)]">
        <nav className="flex justify-between items-center px-6 md:px-8 py-4 max-w-7xl mx-auto">
          <div className="text-xl font-bold tracking-tight text-primary font-headline">AiTherapy</div>
          <div className="text-sm text-on-surface-variant font-medium">Onboarding</div>
        </nav>
      </header>

      {/* ── Content ── */}
      <main className="relative pt-32 pb-20 px-6 flex flex-col items-center justify-center min-h-screen">
        <div className="w-full max-w-2xl relative z-10">

          {/* Header */}
          <div className="mb-8 text-center md:text-left">
            <h1 className="text-4xl md:text-5xl font-extrabold font-headline text-on-surface leading-tight mb-3 tracking-tight">
              Tell us about <span className="text-secondary italic">yourself</span>.
            </h1>
            <p className="text-on-surface-variant text-lg">
              We use this to personalise your therapy experience.
            </p>
          </div>

          <ProgressBar step={step} />

          {/* Error */}
          {error && (
            <div className="mb-6 px-4 py-3 rounded-xl bg-error/10 border border-error/20 text-error text-sm font-medium">
              {error}
            </div>
          )}

          {/* ── Glass Card ── */}
          <div
            key={step}
            className="animate-fade-slide rounded-xl p-8 md:p-12 border border-outline-variant/10 shadow-2xl"
            style={{ background: 'rgba(20, 31, 56, 0.6)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
          >
            {/* Step 0: Identity */}
            {step === 0 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="md:col-span-2 space-y-1.5">
                  <label className={labelClass}>
                    Preferred Name <span className="text-error">*</span>
                  </label>
                  <input
                    id="preferred-name"
                    type="text"
                    placeholder="e.g. Alex"
                    value={form.preferredName}
                    onChange={e => set('preferredName', e.target.value)}
                    maxLength={80}
                    className={inputClass}
                  />
                </div>

                <div className="space-y-1.5">
                  <label className={labelClass}>
                    Age Range <span className="text-error">*</span>
                  </label>
                  <select
                    id="age-range"
                    value={form.ageRange}
                    onChange={e => set('ageRange', e.target.value)}
                    className={inputClass}
                    style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23a3aac4' d='M6 8L1 3h10z'/%3E%3C/svg%3E\")", backgroundRepeat: 'no-repeat', backgroundPosition: 'right 14px center' }}
                  >
                    <option value="">Select age range</option>
                    {AGE_RANGES.map(a => <option key={a} value={a} style={{ background: '#141f38' }}>{a}</option>)}
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className={labelClass}>
                    Gender <span className="text-error">*</span>
                  </label>
                  <select
                    id="gender"
                    value={form.gender}
                    onChange={e => set('gender', e.target.value)}
                    className={inputClass}
                    style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23a3aac4' d='M6 8L1 3h10z'/%3E%3C/svg%3E\")", backgroundRepeat: 'no-repeat', backgroundPosition: 'right 14px center' }}
                  >
                    <option value="">Select gender</option>
                    {GENDERS.map(g => <option key={g} value={g} style={{ background: '#141f38' }}>{g}</option>)}
                  </select>
                </div>

                <div className="md:col-span-2 space-y-1.5">
                  <label className={labelClass}>
                    Country <span className="text-error">*</span>
                  </label>
                  <select
                    id="country"
                    value={form.country}
                    onChange={e => set('country', e.target.value)}
                    className={inputClass}
                    style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23a3aac4' d='M6 8L1 3h10z'/%3E%3C/svg%3E\")", backgroundRepeat: 'no-repeat', backgroundPosition: 'right 14px center' }}
                  >
                    <option value="">Select your country</option>
                    {COUNTRIES.map(c => <option key={c} value={c} style={{ background: '#141f38' }}>{c}</option>)}
                  </select>
                </div>

                <div className="md:col-span-2 text-xs text-on-surface-variant/40 text-right">
                  Fields marked <span className="text-error">*</span> are required.
                </div>
              </div>
            )}

            {/* Step 1: Interests + Communication */}
            {step === 1 && (
              <div className="space-y-8">
                <p className="text-on-surface-variant italic text-sm">
                  Optional — What do you enjoy? This helps your therapist connect with you.
                </p>
                <div>
                  <label className={labelClass}>Things you enjoy</label>
                  <ChipSelect
                    options={INTEREST_OPTIONS}
                    selected={form.interests}
                    onToggle={val => toggleChip('interests', val)}
                    icons={INTEREST_ICONS}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className={labelClass}>Communication style preference</label>
                  <select
                    id="communication-style"
                    value={form.communicationStyle}
                    onChange={e => set('communicationStyle', e.target.value)}
                    className={inputClass}
                    style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23a3aac4' d='M6 8L1 3h10z'/%3E%3C/svg%3E\")", backgroundRepeat: 'no-repeat', backgroundPosition: 'right 14px center' }}
                  >
                    <option value="">No preference</option>
                    <option value="gentle" style={{ background: '#141f38' }}>Gentle &amp; supportive</option>
                    <option value="direct" style={{ background: '#141f38' }}>Direct &amp; straightforward</option>
                    <option value="reflective" style={{ background: '#141f38' }}>Reflective &amp; exploratory</option>
                  </select>
                </div>
              </div>
            )}

            {/* Step 2: Concerns + Note */}
            {step === 2 && (
              <div className="space-y-8">
                <p className="text-on-surface-variant italic text-sm">
                  Optional — What brings you here? Select anything that resonates.
                </p>
                <div>
                  <label className={labelClass}>Areas of concern</label>
                  <ChipSelect
                    options={CONCERNS_OPTIONS}
                    selected={form.concerns}
                    onToggle={val => toggleChip('concerns', val)}
                    icons={CONCERN_ICONS}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className={labelClass}>Anything else you'd like us to know?</label>
                  <textarea
                    id="additional-note"
                    placeholder="Share anything you feel comfortable telling us..."
                    value={form.additionalNote}
                    onChange={e => set('additionalNote', e.target.value)}
                    maxLength={500}
                    className={`${inputClass} min-h-[100px] resize-none`}
                  />
                  <p className="text-xs text-on-surface-variant/40 text-right">{form.additionalNote.length}/500</p>
                </div>
              </div>
            )}

            {/* ── Privacy note ── */}
            <div className="mt-10 flex gap-4 items-start p-5 bg-surface-container-low rounded-xl border-l-4 border-tertiary">
              <span className="text-tertiary text-xl mt-0.5">🛡️</span>
              <div>
                <h4 className="font-bold text-on-surface tracking-tight text-sm">Your data is safe with us.</h4>
                <p className="text-xs text-on-surface-variant leading-relaxed mt-1">
                  AiTherapy uses end-to-end encryption. Your therapist only sees what you explicitly choose to share.
                </p>
              </div>
            </div>
          </div>

          {/* ── Navigation ── */}
          <div className="mt-6 flex items-center justify-between gap-4">
            {step > 0 ? (
              <button
                id="back-btn"
                onClick={handleBack}
                className="flex items-center gap-2 text-on-surface-variant hover:text-on-surface transition-colors font-semibold px-4 py-3"
              >
                ← Back
              </button>
            ) : <div />}

            {step < STEPS.length - 1 ? (
              <button
                id="next-btn"
                onClick={handleNext}
                className="px-10 py-4 rounded-full bg-primary text-on-primary font-extrabold text-base flex items-center gap-3 hover:scale-105 transition-all shadow-[0_10px_30px_rgba(204,151,255,0.25)]"
              >
                Continue
                <span>→</span>
              </button>
            ) : (
              <button
                id="finish-btn"
                onClick={handleSubmit}
                disabled={saving}
                className="px-10 py-4 rounded-full bg-primary text-on-primary font-extrabold text-base flex items-center gap-3 hover:scale-105 transition-all shadow-[0_10px_30px_rgba(204,151,255,0.25)] disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:scale-100"
              >
                {saving ? 'Saving...' : 'Choose my therapist →'}
              </button>
            )}
          </div>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="py-8 px-8 bg-surface-container-lowest">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-4">
          <div className="text-sm font-bold text-primary font-headline">AiTherapy</div>
          <p className="text-xs text-on-surface/40">© 2024 AiTherapy. Secure &amp; Encrypted.</p>
          <div className="flex gap-6">
            {['Privacy Policy', 'Terms of Service'].map(link => (
              <a key={link} href="#" className="text-xs text-on-surface/40 hover:text-secondary transition-colors">{link}</a>
            ))}
          </div>
        </div>
      </footer>
    </div>
  );
}
