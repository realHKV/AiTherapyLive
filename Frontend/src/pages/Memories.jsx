import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import MemoryReview from '../components/MemoryReview';
import ProfileSuggestionReview from '../components/ProfileSuggestionReview';
import ThemeToggle from '../components/ThemeToggle';
import './Home.css';

export default function Memories() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);

  return (
    <div className="home-page">
      <header className="home-header">
        <div className="home-header-inner">
          <button type="button" className="home-brand" onClick={() => navigate('/home')}>TrustTherapy<span>.ai</span></button>
          <ThemeToggle />
        </div>
      </header>
      <main className="home-main home-memories-main">
        <button type="button" className="home-inline-link min-h-11" onClick={() => navigate('/home')}>← Back to overview</button>
        <div className="home-memories-intro">
          <span className="home-card-label">YOUR SPACE</span>
          <h1>Your memories and insights</h1>
          <p>Review what the AI suggested, choose what to keep, and edit or forget saved memories at any time.</p>
        </div>
        <div className="home-memories-grid">
          <article className="home-card"><MemoryReview isOpen refreshKey={0} /></article>
          <article className="home-card">
            <ProfileSuggestionReview isOpen refreshKey={0} onProfileChanged={setProfile} />
            {profile && <p role="status" className="home-saved-note">Your profile was updated.</p>}
          </article>
        </div>
      </main>
    </div>
  );
}
