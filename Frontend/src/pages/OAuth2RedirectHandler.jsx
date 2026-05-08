import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function OAuth2RedirectHandler() {
  const [error, setError] = useState(null);
  const { googleLogin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const hasRun = useRef(false);

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    const params = new URLSearchParams(location.search);
    const token = params.get('token');
    const authError = params.get('error');
    // Backend can optionally signal if this is a brand-new user
    const isNewUser = params.get('new_user') === 'true';

    if (token) {
      googleLogin(token)
        .then(() => {
          // Check if user clicked "Upgrade to Pro" before logging in
          const pendingUpgrade = sessionStorage.getItem('pendingUpgrade');
          if (pendingUpgrade) {
            sessionStorage.removeItem('pendingUpgrade');
            // Open payment link in new tab, then send user to chat
            window.open('https://rzp.io/rzp/PhFTaZ2', '_blank', 'noopener,noreferrer');
            navigate('/chat', { replace: true });
            return;
          }
          if (isNewUser) {
            navigate('/about-you', { replace: true });
          } else {
            navigate('/chat', { replace: true });
          }
        })
        .catch((err) => {
          setError(err.message || 'Failed to complete Google Sign In');
        });
    } else if (authError) {
      setError(authError);
    } else {
      setError('No authentication token found.');
    }
  }, []);

  if (error) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="star-bg" />
        <div className="glass-card" style={{ padding: '36px', maxWidth: '420px', width: '100%', textAlign: 'center', position: 'relative', zIndex: 1 }}>
          <h2 style={{ color: 'var(--error)', marginBottom: '12px' }}>Authentication Error</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '24px' }}>{error}</p>
          <button className="btn-ghost" style={{ width: '100%' }} onClick={() => navigate('/')}>← Back to Home</button>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div className="star-bg" />
      <div style={{ textAlign: 'center', position: 'relative', zIndex: 1 }}>
        <div className="loader" />
        <p style={{ color: 'var(--text-secondary)', marginTop: '16px', fontSize: '15px' }}>
          Signing you in...
        </p>
      </div>
    </div>
  );
}
