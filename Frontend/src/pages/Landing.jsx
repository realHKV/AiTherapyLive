import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import ThemeToggle from '../components/ThemeToggle';
import { useAuth } from '../context/AuthContext';
import { useSubscription } from '../context/subscriptionStore';
import { googleSignInUrl } from '../api/auth';
import { analyticsAvailable, analyticsConsent, recordPublicInteraction, recordPublicLandingVisit, setAnalyticsConsent } from '../analytics/posthog';

// Inline SVG Google logo
const GoogleIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05" />
    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
  </svg>
);

const FEATURES = [
  {
    icon: '🧠',
    matIcon: 'psychology',
    title: 'AI-guided conversation',
    desc: 'Reflect on your thoughts with an AI assistant. It is a support tool, not a licensed therapist.',
    accent: 'primary',
    accentHex: 'rgba(204, 151, 255, 0.12)',
    textColor: 'text-primary',
  },
  {
    icon: '🛡️',
    matIcon: 'verified_user',
    title: 'Sensitive fields encrypted',
    desc: 'Message content and memory text are encrypted by the application before database storage. The service processes text to generate replies.',
    accent: 'secondary',
    accentHex: 'rgba(83, 221, 252, 0.12)',
    textColor: 'text-secondary',
  },
  {
    icon: '🔐',
    matIcon: 'lock',
    title: 'Google sign-in',
    desc: 'Sign in with Google without creating a separate password for this app.',
    accent: 'primary',
    accentHex: 'rgba(204, 151, 255, 0.12)',
    textColor: 'text-primary',
  },
  {
    icon: '🔒',
    matIcon: 'visibility_off',
    title: 'Zero Data Selling',
    desc: 'We do not sell conversation content to advertisers. AI processing requires sending relevant text to the configured model provider.',
    accent: 'tertiary',
    accentHex: 'rgba(155, 255, 206, 0.12)',
    textColor: 'text-tertiary',
  },
];

const FAQ = [
  { question: 'Is TrustTherapy.ai a licensed therapist?', answer: 'No. It is an AI guided reflection tool, not a licensed clinician, diagnosis, treatment, or emergency service. If you need urgent help, contact local emergency services or a qualified professional.' },
  { question: 'Will it remember what I share?', answer: 'Your conversation stays in your chat history. The app may suggest lasting profile details or memories, but you review and apply those suggestions before they are saved for future AI context.' },
  { question: 'Can I return to an unfinished conversation?', answer: 'Yes. When an active chat is available, opening Chat resumes it and shows recent messages. You can start a fresh session when you choose.' },
  { question: 'What is the difference between Free and Pro?', answer: 'Free includes a daily AI usage allowance. Pro provides a higher tier model and expanded usage for 30 days. The app shows your current allowance before you send messages.' },
  { question: 'When is this demo available?', answer: 'The demo backend is scheduled to pause from midnight until 7 a.m. India time. The site remains visible, and a countdown shows when chat is expected to return.' },
];

export default function Landing() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const { isPro, startUpgrade, upgradeLoading, upgradeError } = useSubscription();
  const [activeSection, setActiveSection] = useState('home');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [showAlreadyPro, setShowAlreadyPro] = useState(false);
  const [analyticsEnabled, setAnalyticsEnabled] = useState(analyticsConsent);

  React.useEffect(() => { recordPublicLandingVisit(); }, []);

  React.useEffect(() => {
    // Disable browser's automatic scroll restoration
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
    
    // Clear URL hash to prevent browser jumping to sections
    if (window.location.hash) {
      window.history.replaceState('', document.title, window.location.pathname + window.location.search);
    }

    // Force scroll to top on reload
    window.scrollTo(0, 0);

    const sections = ['home', 'features', 'security', 'pricing', 'faq'];
    const observerOptions = {
      root: null,
      rootMargin: '-40% 0px -40% 0px', // Trigger when section is roughly in the middle 20% of viewport
      threshold: 0
    };

    const observerCallback = (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          setActiveSection(entry.target.id);
        }
      });
    };

    const observer = new IntersectionObserver(observerCallback, observerOptions);

    sections.forEach((id) => {
      const el = document.getElementById(id);
      if (el) observer.observe(el);
    });

    return () => observer.disconnect();
  }, []);

  const handleGoogleSignIn = () => {
    recordPublicInteraction('public_get_started_clicked');
    window.location.href = googleSignInUrl();
  };

  const handleUpgradeToPro = () => {
    if (!isAuthenticated) {
      // Return to the upgrade panel after sign-in.
      sessionStorage.setItem('pendingUpgrade', 'true');
      handleGoogleSignIn();
      return;
    }
    if (isPro) {
      setShowAlreadyPro(true);
      setTimeout(() => { setShowAlreadyPro(false); navigate('/home'); }, 2500);
      return;
    }
    startUpgrade(() => navigate('/home'));
  };

  return (
    <div className="bg-surface text-on-surface font-body selection:bg-primary-container selection:text-on-primary-container min-h-screen overflow-x-hidden">
      {/* Already Pro Toast */}
      {showAlreadyPro && (
        <div className="fixed top-6 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-3 px-6 py-4 rounded-2xl shadow-2xl border border-primary/30 bg-surface-container-high backdrop-blur-sm animate-fade-slide">
          <span className="text-2xl">✨</span>
          <div>
            <p className="font-headline font-bold text-on-surface text-sm">You're already Pro!</p>
            <p className="text-xs text-on-surface-variant">Redirecting you to your session...</p>
          </div>
        </div>
      )}
      {/* TopAppBar */}
      <header className="fixed top-0 w-full z-50 glass-nav border-b border-outline-variant/10 shadow-sm shadow-primary/5">
        <nav className="flex justify-between items-center px-6 py-4 w-full max-w-7xl mx-auto">
          <div className="text-2xl font-bold text-on-surface dark:text-white font-headline tracking-tight leading-tight">
            TrustTherapy.ai
          </div>
          <div className="hidden md:flex items-center gap-8">
            <a 
              className={`font-headline transition-all duration-300 ${activeSection === 'home' ? 'text-primary font-bold scale-110' : 'text-on-surface/70 hover:text-primary hover:opacity-80'}`} 
              href="#home"
            >
              Home
            </a>
            <a 
              className={`font-headline transition-all duration-300 ${activeSection === 'features' ? 'text-primary font-bold scale-110' : 'text-on-surface/70 hover:text-primary hover:opacity-80'}`} 
              href="#features"
            >
              Features
            </a>
            <a 
              className={`font-headline transition-all duration-300 ${activeSection === 'security' ? 'text-primary font-bold scale-110' : 'text-on-surface/70 hover:text-primary hover:opacity-80'}`} 
              href="#security"
            >
              Security
            </a>
            <a 
              className={`font-headline transition-all duration-300 ${activeSection === 'pricing' ? 'text-primary font-bold scale-110' : 'text-on-surface/70 hover:text-primary hover:opacity-80'}`} 
              href="#pricing"
            >
              Pricing
            </a>
            <a className={`font-headline transition-all duration-300 ${activeSection === 'faq' ? 'text-primary font-bold' : 'text-on-surface/70 hover:text-primary'}`} href="#faq">FAQ</a>
            <ThemeToggle />
            <button onClick={handleGoogleSignIn} className="bg-white border border-primary/30 px-6 py-2 rounded-full font-headline text-black font-semibold cursor-pointer active:scale-95 transition-all outline outline-1 outline-outline-variant/20">Sign In</button>
          </div>
          <div className="md:hidden">
            <button type="button" aria-label="Toggle navigation" aria-expanded={isMobileMenuOpen} onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)} className="min-w-11 min-h-11 flex items-center justify-center -mr-2">
              <span className="material-symbols-outlined text-on-surface">menu</span>
            </button>
          </div>
        </nav>
        {/* Mobile Menu */}
        {isMobileMenuOpen && (
          <div className="absolute top-full left-0 w-full bg-surface-container shadow-xl border-b border-outline-variant/10 md:hidden flex flex-col px-6 py-6 gap-4 z-[60] animate-fade-slide">
            <a href="#home" onClick={() => setIsMobileMenuOpen(false)} className="text-on-surface font-headline font-bold text-lg py-2">Home</a>
            <a href="#features" onClick={() => setIsMobileMenuOpen(false)} className="text-on-surface font-headline font-bold text-lg py-2">Features</a>
            <a href="#security" onClick={() => setIsMobileMenuOpen(false)} className="text-on-surface font-headline font-bold text-lg py-2">Security</a>
            <a href="#pricing" onClick={() => setIsMobileMenuOpen(false)} className="text-on-surface font-headline font-bold text-lg py-2">Pricing</a>
            <a href="#faq" onClick={() => setIsMobileMenuOpen(false)} className="text-on-surface font-headline font-bold text-lg py-2">FAQ</a>
            <button onClick={() => { handleGoogleSignIn(); setIsMobileMenuOpen(false); }} className="bg-primary text-on-primary px-6 py-3 rounded-full font-headline font-bold text-center mt-2">Sign In with Google</button>
          </div>
        )}
      </header>

      <main className="pt-16 md:pt-24">
        {/* Hero Section Wrapper */}
        <div id="home" className="relative w-full min-h-[90vh] flex items-center justify-center overflow-hidden">
          {/* Spline Full Background */}
          <div className="absolute inset-0 z-0 bg-surface">
            {/* Touch/Gyro Blocker Overlay for Mobile */}
            <div className="md:hidden absolute inset-0 z-10 w-full h-full"></div>
            {/* Desktop: Animated 3D Galaxy */}
            <iframe 
              src="https://my.spline.design/galaxy-1G5qZ7rvP9fNeubiNbAU1an2/" 
              frameBorder="0" 
              width="100%" 
              height="100%"
              title="Spline 3D ModelDesktop"
              allow="accelerometer 'none'; gyroscope 'none'; magnetometer 'none'"
              style={{ transform: 'translateZ(0)', willChange: 'transform' }}
              className="hidden md:block w-full h-full pointer-events-none transition-all duration-1000 invert hue-rotate-180 brightness-[1.2] contrast-[1.1] dark:invert-0 dark:hue-rotate-0 dark:brightness-100 dark:contrast-100"
            ></iframe>

            {/* Mobile: Static Fallback Galaxy */}
            <div className="md:hidden w-full h-full relative overflow-hidden bg-surface dark:bg-[#060e20]">
              <div className="absolute top-10 -left-10 w-72 h-72 bg-[#cc97ff]/20 rounded-full blur-[60px]"></div>
              <div className="absolute top-[40%] -right-10 w-72 h-72 bg-[#53ddfc]/15 rounded-full blur-[70px]"></div>
              <div className="absolute bottom-10 left-[20%] w-60 h-60 bg-[#cc97ff]/10 rounded-full blur-[60px]"></div>
            </div>
          </div>

          {/* Hero Content Overlay (pointer-events-none allows interacting with 3D model through empty space) */}
          <section className="relative z-10 px-6 py-6 md:py-20 max-w-7xl mx-auto flex flex-col items-center text-center pointer-events-none">
            <div className="absolute -top-20 -left-20 w-96 h-96 bg-primary-container/30 rounded-full blur-[100px] -z-10 hidden md:block"></div>
            <div className="absolute top-40 -right-20 w-80 h-80 bg-secondary-container/20 rounded-full blur-[80px] -z-10 hidden md:block"></div>

            <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-surface-container-low mb-8 border border-outline-variant/10 shadow-sm pointer-events-auto">
              <span className="w-2 h-2 rounded-full bg-primary animate-pulse"></span>
              <span className="text-xs font-semibold uppercase tracking-widest text-on-surface-variant font-label">Now in Public Beta</span>
            </div>

            <h1 className="font-headline text-[clamp(2.5rem,12vw,3rem)] md:text-7xl lg:text-8xl font-extrabold tracking-tighter leading-[0.95] md:leading-[0.9] mb-8 text-glow pointer-events-auto">
              Healing at the <br />
              <span className="text-primary italic drop-shadow-xl">speed of thought.</span>
            </h1>

            <p className="max-w-2xl text-base sm:text-lg md:text-xl text-on-surface-variant leading-relaxed mb-8 md:mb-12 font-body font-semibold pointer-events-auto bg-surface/50 backdrop-blur-sm p-4 rounded-2xl">
              Experience a restorative journey with TrustTherapy.ai. Our empathetic AI creates a safe, judgment-free space designed to help you navigate life's complexities with clarity and calm.
            </p>

            <div className="flex flex-col sm:flex-row gap-4 items-center w-full sm:w-auto pointer-events-auto">
              <button onClick={handleGoogleSignIn} className="liquid-gradient text-on-primary w-full sm:w-auto justify-center px-4 sm:px-10 py-4 rounded-full font-headline font-bold text-base sm:text-lg flex items-center gap-2 sm:gap-3 shadow-xl shadow-primary/10 hover:shadow-primary/20 active:scale-95 transition-all">
                <span>Begin Your Journey</span>
                <span className="material-symbols-outlined">arrow_forward</span>
              </button>
              <button onClick={handleGoogleSignIn} className="bg-white border border-primary/30 w-full sm:w-auto justify-center px-4 sm:px-8 py-4 rounded-full font-headline font-semibold text-base sm:text-lg flex items-center gap-2 sm:gap-3 active:scale-95 transition-all text-gray-900 shadow-[0_0_20px_rgba(255,107,0,0.25)] hover:shadow-[0_0_30px_rgba(255,107,0,0.4)]">
                <GoogleIcon />
                <span>Continue with Google</span>
              </button>
            </div>
          </section>
        </div>

        {/* Why AI Therapy Section */}
        <section id="features" className="py-24 px-6 max-w-7xl mx-auto">
          <h2 className="font-headline text-4xl md:text-5xl font-bold mb-8 tracking-tight text-center">Why AI Therapy?</h2>

          {/* Context / Mission Statement Paragraph */}
          <div className="mb-16 rounded-3xl border border-outline-variant/15 bg-surface-container-low/60 backdrop-blur-sm p-8 md:p-12 relative overflow-hidden">
            {/* Subtle accent glow */}
            <div className="absolute -top-16 -left-16 w-64 h-64 bg-primary/10 rounded-full blur-3xl pointer-events-none" />
            <div className="absolute -bottom-16 -right-16 w-56 h-56 bg-secondary/8 rounded-full blur-3xl pointer-events-none" />

            <div className="relative z-10 text-center">
              {/* Eyebrow label */}
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary/10 border border-primary/20 mb-6 mx-auto">
                <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                <span className="text-xs font-bold uppercase tracking-widest text-primary font-label">The Reality</span>
              </div>

              <p className="font-body text-lg md:text-xl text-on-surface leading-relaxed mb-6">
                Stress, loneliness, grief, and uncertainty can make it difficult to pause and reflect. An AI conversation can offer a place to organize your thoughts when you want to write them down.
              </p>

              <p className="font-body text-base md:text-lg text-on-surface-variant leading-relaxed mb-8">
                Professional care can involve cost, travel, or scheduling. This app provides a separate self-reflection tool; it does not diagnose conditions, deliver treatment, or replace a qualified professional.
              </p>

              {/* Three highlight points */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
                {[
                  { icon: '💸', label: 'No charge to start', desc: 'The Free tier has a daily AI usage allowance.' },
                  { icon: '🗓️', label: 'Use on your schedule', desc: 'Write when the service is available, without booking a session.' },
                  { icon: '📝', label: 'Reflect privately', desc: 'Review and control the memories the AI may use in future chats.' },
                ].map(({ icon, label, desc }) => (
                  <div key={label} className="flex flex-col gap-2 p-4 rounded-2xl bg-surface-container border border-outline-variant/10">
                    <span className="text-2xl">{icon}</span>
                    <h4 className="font-headline font-bold text-sm text-on-surface">{label}</h4>
                    <p className="font-body text-xs text-on-surface-variant leading-relaxed">{desc}</p>
                  </div>
                ))}
              </div>

              {/* Honest disclaimer / mission statement */}
              <div className="flex gap-4 p-5 rounded-2xl border-l-4 border-primary bg-primary/5 text-left">
                <span className="material-symbols-outlined text-primary text-2xl flex-shrink-0 mt-0.5">info</span>
                <p className="font-body text-sm md:text-base text-on-surface-variant leading-relaxed">
                  <span className="font-semibold text-on-surface">TrustTherapy is not a replacement for professional mental health care.</span> If you are in immediate danger or experiencing a crisis, contact local emergency services or a qualified crisis service. The AI can help with self-reflection but cannot provide emergency care.
                </p>
              </div>
            </div>
          </div>

          {/* What We Provide sub-header */}
          <div className="text-center mb-10">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-secondary/10 border border-secondary/20 mb-5">
              <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
              <span className="text-xs font-bold uppercase tracking-widest text-secondary font-label">What We Provide</span>
            </div>
            <h3 className="font-headline text-3xl md:text-4xl font-bold tracking-tight mb-3">Built for the moments<br /><span className="text-primary italic">between you and help.</span></h3>
            <p className="text-on-surface-variant font-body max-w-xl mx-auto text-base md:text-lg">
              Every feature is designed around one goal — making emotional support feel as natural and effortless as talking to a trusted friend.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-12 gap-6 md:auto-rows-[250px]">

            <div className="md:col-span-8 bg-surface-container-low rounded-3xl p-6 sm:p-10 min-h-[230px] flex flex-col justify-end relative overflow-hidden group border border-outline-variant/10">
              <div className="absolute top-0 right-0 w-1/2 h-full opacity-10 group-hover:scale-110 transition-transform duration-700">
                <span className="material-symbols-outlined text-[300px] text-primary" style={{ fontVariationSettings: "'wght' 100" }}>schedule</span>
              </div>
              <div className="relative z-10">
                <h3 className="font-headline text-3xl font-bold mb-4">Radical Accessibility</h3>
                <p className="text-on-surface-variant max-w-md font-body leading-relaxed">No appointment is needed. You can start a conversation when the service is available and your daily allowance permits.</p>
              </div>
            </div>
            <div className="md:col-span-4 bg-primary text-on-primary rounded-3xl p-6 sm:p-10 min-h-[230px] flex flex-col justify-center text-center items-center">
              <span className="material-symbols-outlined text-6xl mb-6" style={{ fontVariationSettings: "'wght' 200" }}>diversity_2</span>
              <h3 className="font-headline text-2xl font-bold mb-2">Zero Judgment</h3>
              <p className="text-on-primary/80 text-sm">Speak your truth without the fear of being perceived or judged by another human being.</p>
            </div>
            <div className="md:col-span-4 bg-surface-container-high rounded-3xl p-6 sm:p-10 min-h-[230px] flex flex-col justify-between border border-outline-variant/10">
              <span className="material-symbols-outlined text-primary text-4xl">psychology</span>
              <div>
                <h3 className="font-headline text-xl font-bold mb-2">Guided reflection</h3>
                <p className="text-on-surface-variant text-sm">The AI can help organize thoughts and suggest questions for reflection. It does not provide diagnosis or treatment.</p>
              </div>
            </div>
            <div className="md:col-span-8 bg-surface-container-lowest rounded-3xl p-6 sm:p-10 flex flex-col md:flex-row items-center gap-6 md:gap-10 shadow-sm border border-outline-variant/10">
              <div className="w-full md:w-1/2">
                <h3 className="font-headline text-3xl font-bold mb-4">Context you control</h3>
                <p className="text-on-surface-variant font-body">Confirmed memories can inform later conversations. You can review, edit, resolve, and forget saved memories.</p>
              </div>
              <div className="w-full md:w-1/2 h-36 md:h-full rounded-2xl overflow-hidden">
                <img alt="Evolving Calm" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuB04KGNTiDoDdkEPu5jRGzHrseYA60wmku-hgBvDeKgPYTM5HgRv33CPlakORH0MiptATgCrrcLHp7LoNV2nIrqMgUDZJuaFXNsYzGMQvMz_7ez8MYSa38qPCOd4rQXvtA3J5NyUiHcuEm2VflEfeOvDlzEU5VLKHWwbD5PMER3SdQvq0ist6gnQ7DZuX0IcnklUZuMygQrm-tcZkOuHXvc2XVFmDmGBmw3JDbf1lYLjt6FYxLwPuzRQuz9G58t3DtS_h5Uk5FIU_1s" />
              </div>
            </div>
          </div>
        </section>

        {/* Security & Privacy Section */}
        <section id="security" className="bg-surface-container-low py-20 md:py-32 px-6">
          <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-2 gap-20 items-center">
            <div>
              <h2 className="font-headline text-4xl md:text-6xl font-bold mb-8 tracking-tighter">Your mind is your <br /><span className="text-primary italic">private sanctuary.</span></h2>
              <p className="text-lg text-on-surface-variant mb-12 max-w-xl font-body leading-relaxed">
                Message and memory text are encrypted before database storage. To generate a reply, the service sends relevant conversation content to its configured AI provider.
              </p>
              <div className="space-y-6">
                <div className="flex gap-4">
                  <div className="w-12 h-12 rounded-full bg-surface-container-highest flex items-center justify-center flex-shrink-0">
                    <span className="material-symbols-outlined text-primary">lock</span>
                  </div>
                  <div>
                    <h4 className="font-headline font-bold text-lg">Encrypted storage</h4>
                    <p className="text-on-surface-variant text-sm">Sensitive text fields are encrypted by the application before they are stored.</p>
                  </div>
                </div>
                <div className="flex gap-4">
                  <div className="w-12 h-12 rounded-full bg-surface-container-highest flex items-center justify-center flex-shrink-0">
                    <span className="material-symbols-outlined text-primary">shield_person</span>
                  </div>
                  <div>
                    <h4 className="font-headline font-bold text-lg">Memory controls</h4>
                    <p className="text-on-surface-variant text-sm">Review suggested memories, edit saved ones, or forget them from future AI context.</p>
                  </div>
                </div>
              </div>
            </div>
            <div className="relative">
              <div className="absolute -inset-4 bg-primary/20 blur-3xl rounded-full"></div>
              <div className="relative p-6 sm:p-12 rounded-[2rem] sm:rounded-[3rem] shadow-xl shadow-black/20 border border-outline-variant/10 group overflow-hidden" style={{ background: 'oklch(27.4% 0.006 286.033)' }}>
                {/* Subtle dark gradient overlay */}
                <div className="absolute inset-0 bg-gradient-to-br from-white/5 to-transparent pointer-events-none"></div>
                
                <div className="relative z-10">
                  <div className="mb-10 flex justify-center">
                    <div className="w-24 h-24 rounded-full bg-primary/20 flex items-center justify-center text-primary">
                      <span className="material-symbols-outlined text-5xl">verified_user</span>
                    </div>
                  </div>
                  <div className="text-center">
                    <h3 className="font-headline text-2xl font-bold mb-4 text-white">Designed for careful handling</h3>
                    <p className="text-white/70 mb-8 font-body">The application encrypts sensitive stored text and checks usage limits on the server.</p>
                    <div className="flex flex-wrap justify-around gap-4 py-6 border-t border-white/10">
                      <div className="text-center">
                        <p className="text-2xl font-extrabold font-headline text-primary">256-bit</p>
                        <p className="text-[10px] uppercase tracking-widest font-label text-white/50">Stored text encryption</p>
                      </div>
                      <div className="text-center">
                        <p className="text-2xl font-extrabold font-headline text-primary">Limits</p>
                        <p className="text-[10px] uppercase tracking-widest font-label text-white/50">Server-enforced usage</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Pricing Section */}
        <section id="pricing" className="py-28 px-6">
          <div className="max-w-5xl mx-auto">
            <div className="text-center mb-16">
              <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-primary/10 border border-primary/20 mb-5">
                <span className="w-2 h-2 rounded-full bg-primary"></span>
                <span className="text-xs font-bold uppercase tracking-widest text-primary font-label">Simple Pricing</span>
              </div>
              <h2 className="font-headline text-4xl md:text-5xl font-bold tracking-tight mb-4">Choose your plan</h2>
              <p className="text-on-surface-variant text-lg max-w-xl mx-auto">Start free forever, upgrade when you're ready for more.</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-start">
              {/* Free Card */}
              <div className="relative rounded-3xl border border-outline-variant/20 bg-surface-container-low p-8 flex flex-col gap-6">
                <div>
                  <h3 className="font-headline text-2xl font-bold mb-1">Free</h3>
                  <p className="text-on-surface-variant text-sm">Everything you need to start your journey.</p>
                </div>
                <div className="flex items-end gap-1">
                  <span className="font-headline text-5xl font-extrabold">₹0</span>
                  <span className="text-on-surface-variant mb-2">free</span>
                </div>
                <ul className="space-y-3 text-sm">
                  {[
                    'Daily AI usage allowance',
                    'AI that learns your history',
                    'Sensitive text encrypted at rest',
                    'Long-term memory & profile',
                  ].map(f => (
                    <li key={f} className="flex items-center gap-3 text-on-surface-variant">
                      <span className="w-5 h-5 rounded-full bg-surface-container-highest flex items-center justify-center text-primary text-xs">✓</span>
                      {f}
                    </li>
                  ))}
                </ul>
                <button
                  onClick={handleGoogleSignIn}
                  className="w-full py-3 rounded-full border border-outline-variant/30 font-headline font-semibold text-on-surface hover:bg-surface-container transition-all"
                >
                  Get started free
                </button>
              </div>

              {/* Pro Card */}
              <div className="relative rounded-3xl border border-primary/40 p-8 flex flex-col gap-6 shadow-[0_0_60px_rgba(204,151,255,0.15)] overflow-hidden"
                style={{ background: 'linear-gradient(145deg, rgba(204,151,255,0.08) 0%, rgba(83,221,252,0.05) 100%)' }}>
                {/* Glow blob */}
                <div className="absolute -top-10 -right-10 w-40 h-40 bg-primary/20 rounded-full blur-3xl pointer-events-none"></div>

                {/* Most popular badge */}
                <div className="absolute top-5 right-5 px-3 py-1 rounded-full bg-primary text-on-primary text-xs font-bold font-label">✨ Most Popular</div>

                <div>
                  <h3 className="font-headline text-2xl font-bold mb-1 text-primary">Pro</h3>
                  <p className="text-on-surface-variant text-sm">Unlock a more capable AI guide.</p>
                </div>
                <div className="flex items-end gap-1">
                  <span className="font-headline text-5xl font-extrabold">₹50</span>
                  <span className="text-on-surface-variant mb-2">for 30 days</span>
                </div>
                <ul className="space-y-3 text-sm">
                  {[
                    'Everything in Free',
                    'Smarter AI model (priority)',
                    'Faster, richer responses',
                    'Early access to new features',
                  ].map(f => (
                    <li key={f} className="flex items-center gap-3">
                      <span className="w-5 h-5 rounded-full bg-primary/20 flex items-center justify-center text-primary text-xs">✓</span>
                      <span className="text-on-surface font-medium">{f}</span>
                    </li>
                  ))}
                </ul>
                <button
                  onClick={handleUpgradeToPro}
                  disabled={upgradeLoading}
                  className="w-full py-3 rounded-full liquid-gradient text-on-primary font-headline font-bold text-center shadow-lg shadow-primary/20 hover:shadow-primary/40 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-50"
                >
                  {isAuthenticated && isPro ? '✨ Go to Chat (Already Pro)' : upgradeLoading ? 'Preparing checkout...' : 'Get 30 days of Pro →'}
                </button>
                <p className="text-center text-xs text-on-surface-variant/50">One-time payment via Razorpay</p>
                {upgradeError && <p role="alert" className="text-xs text-error">{upgradeError}</p>}
              </div>
            </div>
          </div>
        </section>

        <section id="faq" aria-labelledby="faq-title" className="py-24 px-6 bg-surface-container-low">
          <div className="max-w-4xl mx-auto">
            <p className="text-xs font-bold uppercase tracking-widest text-primary mb-4">Good to know</p>
            <h2 id="faq-title" className="font-headline text-4xl md:text-5xl font-bold tracking-tight mb-5">Questions before you begin</h2>
            <p className="text-on-surface-variant mb-10">Clear answers about the AI guide, saved memories, pricing, and demo hours.</p>
            <div className="space-y-3">
              {FAQ.map(({ question, answer }) => (
                <details key={question} onToggle={event => { if (event.currentTarget.open) recordPublicInteraction('public_faq_opened'); }} className="group rounded-2xl border border-outline-variant/20 bg-surface p-5 md:p-6">
                  <summary className="cursor-pointer list-none flex items-center justify-between gap-4 font-headline font-semibold text-lg marker:hidden">
                    {question}<span aria-hidden="true" className="material-symbols-outlined text-primary group-open:rotate-45 transition-transform">add</span>
                  </summary>
                  <p className="mt-4 text-on-surface-variant leading-relaxed max-w-3xl">{answer}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section className="py-32 px-6 max-w-4xl mx-auto text-center">
          <h2 className="font-headline text-4xl md:text-5xl font-bold mb-8 tracking-tight">Ready for clarity?</h2>
          <p className="text-xl text-on-surface-variant mb-12 font-body font-light">Begin your journey to a calmer, clearer mind today. Start your first session in seconds.</p>
          <button onClick={handleGoogleSignIn} className="liquid-gradient text-on-primary px-12 py-5 rounded-full font-headline font-bold text-xl shadow-2xl shadow-primary/20 hover:scale-105 active:scale-95 transition-transform">
            Get Started for Free
          </button>
        </section>
      </main>

      {/* Footer */}
      <footer className="bg-surface-container-low w-full py-12 border-t border-outline-variant/10">
        <div className="flex flex-col md:flex-row justify-between items-center px-8 w-full max-w-7xl mx-auto gap-8">
          <div className="flex flex-col items-center md:items-start">
            <div className="text-lg font-headline font-bold text-[#173356] dark:text-[#f9f9ff] mb-2">TrustTherapy.ai</div>
            <div className="font-body text-sm text-[#173356]/60 dark:text-[#f9f9ff]/60">© 2026 TrustTherapy.ai. A restorative journey.</div>
          </div>
          <div className="flex gap-12">
            <Link to="/privacy" className="font-body text-sm text-[#173356]/60 dark:text-[#f9f9ff]/60 hover:text-primary underline-offset-4 hover:underline transition-all cursor-pointer">Privacy</Link>
            <Link to="/terms" className="font-body text-sm text-[#173356]/60 dark:text-[#f9f9ff]/60 hover:text-primary underline-offset-4 hover:underline transition-all cursor-pointer">Terms</Link>
            <a href="mailto:imnotharsh14@gmail.com" className="font-body text-sm text-[#173356]/60 dark:text-[#f9f9ff]/60 hover:text-primary underline-offset-4 hover:underline transition-all cursor-pointer">Support</a>
          </div>
          <div className="flex gap-4">
            <a href="mailto:imnotharsh14@gmail.com" className="flex items-center">
              <span className="material-symbols-outlined text-[#173356]/40 cursor-pointer hover:text-primary transition-colors">mail</span>
            </a>
          </div>
          {analyticsAvailable && (
            <label className="flex items-center gap-2 text-xs text-on-surface-variant cursor-pointer">
              <input type="checkbox" checked={analyticsEnabled} onChange={event => { setAnalyticsConsent(event.target.checked); setAnalyticsEnabled(event.target.checked); }} />
              Allow landing page analytics without account identity
            </label>
          )}
        </div>
      </footer>
    </div>
  );
}
