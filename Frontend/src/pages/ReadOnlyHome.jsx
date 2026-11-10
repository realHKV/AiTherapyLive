import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import ThemeToggle from '../components/ThemeToggle';
import './Home.css';

export default function ReadOnlyHome() {
  const { logout } = useAuth();
  const [logoutError, setLogoutError] = useState('');

  const handleLogout = async () => {
    try { await logout(); }
    catch { setLogoutError('Could not sign out. Please try again.'); }
  };

  return (
    <div className="home-page">
      <div className="home-ambient home-ambient-one" aria-hidden="true" />
      <div className="home-ambient home-ambient-two" aria-hidden="true" />
      <header className="home-header">
        <div className="home-header-inner">
          <span className="home-brand">TrustTherapy<span>.ai</span></span>
          <div className="home-header-actions">
            <ThemeToggle />
            <button type="button" className="home-text-button" onClick={handleLogout}>Sign out</button>
          </div>
        </div>
      </header>
      <main className="home-main">
        <div className="home-eyebrow"><span className="home-live-dot" /> YOUR SPACE</div>
        <section className="home-intro">
          <div>
            <p className="home-kicker">A moment to check in</p>
            <h1>Welcome to <em>TrustTherapy.</em></h1>
            <p className="home-intro-copy">Explore our approach to guided reflection and the plan available when you are ready.</p>
          </div>
        </section>
        <section className="home-grid" aria-label="Product overview">
          <div className="home-main-column">
            <article className="home-session-card">
              <div className="home-session-content">
                <span className="home-card-label">A SPACE TO REFLECT</span>
                <h2>Thoughtful conversations, at your pace.</h2>
                <p>TrustTherapy is an AI self-reflection tool. Conversations can help you organize your thoughts and consider a next step. It is not a licensed therapist or emergency service.</p>
                <Link className="home-primary-button inline-flex items-center" to="/">Explore the website <span aria-hidden="true">↗</span></Link>
              </div>
            </article>
            <div className="home-gentle-note"><span aria-hidden="true">✦</span><p>Take your time.<br /><strong>Your space can begin with a single thought.</strong></p></div>
          </div>
          <div className="home-side-column">
            <article className="home-card home-plan-card">
              <span className="home-card-label">REFLECTION PLUS</span>
              <div className="home-plan-title"><h2>₹100</h2><span className="home-plan-badge">30 DAYS</span></div>
              <p>A one-time, non-renewing purchase with a higher tier AI model and expanded usage.</p>
              <p className="home-plan-terms">This account can browse the service. Conversations and purchases are unavailable on this account.</p>
            </article>
            <article className="home-card home-profile-card">
              <span className="home-card-label">YOUR PRIVACY</span>
              <h2>Built for careful reflection</h2>
              <p>Account holders can review saved memories, and sensitive conversation content is encrypted before database storage.</p>
              <div className="flex gap-4 mt-5 text-sm font-semibold">
                <Link to="/privacy" className="text-primary hover:underline">Privacy</Link>
                <Link to="/terms" className="text-primary hover:underline">Terms</Link>
              </div>
            </article>
          </div>
        </section>
        {logoutError && <p className="home-inline-error" role="alert">{logoutError}</p>}
      </main>
    </div>
  );
}
