import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useSubscription } from '../context/subscriptionStore';
import { profileAPI } from '../api/profile';
import ThemeToggle from '../components/ThemeToggle';
import './Home.css';

const guides = {
  dr_sarah: { name: 'Sarah', initial: 'S', tone: 'Gentle and reflective', focus: 'Emotional exploration', color: 'home-guide-sarah' },
  dr_alex: { name: 'Alex', initial: 'A', tone: 'Direct and practical', focus: 'Action planning', color: 'home-guide-alex' },
};

const displayName = (profile, user) => profile?.preferredName?.trim() || user?.displayName?.trim() || 'there';

export default function Home() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const { isPro, proExpiresAt, loading: planLoading, statusLoaded, refreshStatus, startUpgrade, upgradeLoading, upgradeError } = useSubscription();
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState('');
  const [logoutError, setLogoutError] = useState('');

  useEffect(() => {
    let active = true;
    profileAPI.getProfile()
      .then(data => {
        if (!active) return;
        if (!data?.onboardingComplete) {
          navigate('/about-you', { replace: true });
          return;
        }
        setProfile(data);
      })
      .catch(() => { if (active) setProfileError('Your overview could not be loaded. Please try again.'); })
      .finally(() => { if (active) setProfileLoading(false); });
    return () => { active = false; };
  }, [navigate]);

  const guide = guides[profile?.personaId] || null;
  const planName = statusLoaded ? (isPro ? 'Pro' : 'Free') : 'Checking plan';
  const proEnd = proExpiresAt && !Number.isNaN(Date.parse(proExpiresAt))
    ? new Date(proExpiresAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : null;

  const handleLogout = async () => {
    try { await logout(); }
    catch { setLogoutError('Could not sign out. Please try again.'); }
  };

  if (profileLoading && !profileError) {
    return <div className="home-page home-loading" role="status">Loading your space...</div>;
  }

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
        {profileError ? (
          <section className="home-error" role="alert">
            <h1>We couldn’t load your space</h1>
            <p>{profileError}</p>
            <button type="button" className="home-primary-button" onClick={() => window.location.reload()}>Try again</button>
          </section>
        ) : (
          <>
            <div className="home-eyebrow"><span className="home-live-dot" /> YOUR SPACE</div>
            <section className="home-intro">
              <div>
                <p className="home-kicker">A moment to check in</p>
                <h1>Welcome back, <em>{displayName(profile, user)}.</em></h1>
                <p className="home-intro-copy">Your space to reflect is ready. Pick up your conversation whenever you feel like talking.</p>
              </div>
              <div className="home-date" aria-label="Today">
                <span>Today</span>
                <strong>{new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'long' })}</strong>
              </div>
            </section>

            <section className="home-grid" aria-label="Your overview">
              <div className="home-main-column">
                <article className="home-session-card">
                  <div className="home-session-art" aria-hidden="true"><span>✦</span><span>✳</span><span>✦</span></div>
                  <div className="home-session-content">
                    <span className="home-card-label">YOUR NEXT STEP</span>
                    <h2>There’s room for whatever is on your mind.</h2>
                    <p>Start or continue a private conversation with your chosen AI guide. Your saved memories stay under your control.</p>
                    <button type="button" className="home-primary-button" onClick={() => navigate('/chat')}>
                      Go to chat <span aria-hidden="true">↗</span>
                    </button>
                  </div>
                </article>

                <article className="home-card home-guide-card">
                  <div className="home-section-heading">
                    <div><span className="home-card-label">YOUR AI GUIDE</span><h2>Who you’ll talk with</h2></div>
                    <button type="button" className="home-inline-link" onClick={() => navigate('/choose-therapist', { state: { fromHome: true, currentGuide: profile.personaId } })}>Change guide <span aria-hidden="true">↗</span></button>
                  </div>
                  {guide ? (
                    <div className="home-guide-details">
                      <div className={`home-guide-avatar ${guide.color}`} aria-hidden="true">{guide.initial}</div>
                      <div className="home-guide-copy">
                        <h3>{guide.name}</h3>
                        <p>{guide.tone} · {guide.focus}</p>
                        <span>AI companion persona</span>
                      </div>
                    </div>
                  ) : (
                    <div className="home-guide-empty">
                      <p>Choose an AI guide before you begin.</p>
                      <button type="button" className="home-inline-link" onClick={() => navigate('/choose-therapist', { state: { fromHome: true } })}>Choose guide →</button>
                    </div>
                  )}
                </article>
              </div>

              <div className="home-side-column">
                <article className="home-card home-plan-card">
                  <span className="home-card-label">YOUR VERSION</span>
                  <div className="home-plan-title"><h2>{planName}</h2><span className={isPro && statusLoaded ? 'home-plan-badge pro' : 'home-plan-badge'}>{statusLoaded ? 'ACTIVE' : 'UNKNOWN'}</span></div>
                  <p>{statusLoaded ? (isPro ? (proEnd ? `Pro access until ${proEnd}` : 'Pro access is active') : 'Daily AI usage allowance') : 'We could not verify your current plan yet.'}</p>
                  {!statusLoaded && !planLoading && <button type="button" className="home-inline-link" onClick={refreshStatus}>Check plan again</button>}
                  {statusLoaded && !isPro && (
                    <button type="button" className="home-inline-link" disabled={upgradeLoading} onClick={() => startUpgrade()}>
                      {upgradeLoading ? 'Opening checkout...' : 'Explore Pro'} <span aria-hidden="true">↗</span>
                    </button>
                  )}
                  {upgradeError && <p className="home-inline-error" role="alert">{upgradeError}</p>}
                  {location.state?.showUpgrade && !isPro && <p className="home-inline-note">Your plan is shown here before you decide whether to upgrade.</p>}
                </article>

                <article className="home-card home-profile-card">
                  <div className="home-section-heading"><div><span className="home-card-label">ABOUT YOU</span><h2>Your profile</h2></div></div>
                  <dl className="home-profile-list">
                    <div><dt>Name</dt><dd>{displayName(profile, user)}</dd></div>
                    <div><dt>Age range</dt><dd>{profile.ageRange || 'Not shared'}</dd></div>
                    <div><dt>Communication</dt><dd>{profile.communicationStyle || 'Default'}</dd></div>
                    <div><dt>Sessions completed</dt><dd>{profile.totalSessions ?? 0}</dd></div>
                  </dl>
                  <div className="home-topic-area">
                    <span className="home-card-label">WHAT MATTERS TO YOU</span>
                    {profile.topicsOfConcern?.length ? (
                      <div className="home-topics">{profile.topicsOfConcern.slice(0, 4).map(topic => <span key={topic}>{topic}</span>)}</div>
                    ) : <p>No topics saved yet. You can add them during onboarding or through your profile.</p>}
                  </div>
                  <div className="home-insights-area">
                    <span className="home-card-label">SAVED INSIGHTS</span>
                    {profile.traits?.length ? (
                      <ul>{profile.traits.slice(0, 2).map(trait => <li key={trait.key}><strong>{trait.key}</strong><span>{trait.value}</span></li>)}</ul>
                    ) : <p>Insights you choose to save from chats will appear here.</p>}
                  </div>
                  <button type="button" className="home-memory-link" onClick={() => navigate('/memories')}>
                    Review memories and suggestions <span aria-hidden="true">↗</span>
                  </button>
                </article>
              </div>
            </section>

            <p className="home-footer-note">TrustTherapy is an AI self-reflection tool, not a replacement for professional mental health care.</p>
          </>
        )}
        {logoutError && <p className="home-inline-error" role="alert">{logoutError}</p>}
      </main>
    </div>
  );
}
