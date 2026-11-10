import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { googleSignInUrl } from '../api/auth';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError('');
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate('/home', { replace: true });
    } catch (err) {
      setError(err?.response?.data?.error?.message || 'Sign-in failed. Check your details and try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface text-on-surface font-body relative overflow-hidden">
      <div className="welcome-atmosphere" aria-hidden="true"><span /><span /><span /></div>
      <header className="relative z-10 max-w-7xl mx-auto px-6 py-6 flex items-center justify-between">
        <Link to="/" className="font-headline text-xl font-extrabold tracking-tight">TrustTherapy<span className="text-primary">.ai</span></Link>
        <Link to="/" className="text-sm font-semibold text-on-surface-variant hover:text-primary">Back to home</Link>
      </header>

      <main className="relative z-10 min-h-[calc(100vh-88px)] flex items-center justify-center px-5 py-12">
        <section className="w-full max-w-md rounded-[2rem] border border-outline-variant/20 bg-surface-container-low/90 backdrop-blur-xl shadow-2xl shadow-primary/10 p-7 sm:p-10">
          <div className="inline-flex items-center justify-center h-12 w-12 rounded-2xl bg-primary/15 text-primary mb-6" aria-hidden="true">
            <span className="material-symbols-outlined">waving_hand</span>
          </div>
          <h1 className="font-headline text-3xl sm:text-4xl font-extrabold tracking-tight">Welcome back</h1>
          <p className="text-on-surface-variant mt-3 mb-8">Sign in to your space and pick up where you left off.</p>

          <button type="button" onClick={() => { window.location.href = googleSignInUrl(); }}
            className="w-full min-h-12 rounded-xl bg-white text-gray-900 border border-outline-variant/30 font-bold hover:shadow-lg transition-shadow flex items-center justify-center gap-3">
            <span className="font-bold text-lg text-[#4285f4]" aria-hidden="true">G</span> Continue with Google
          </button>

          <div className="flex items-center gap-4 my-7 text-xs uppercase tracking-widest text-on-surface-variant" aria-hidden="true">
            <span className="h-px bg-outline-variant/30 flex-1" /> or sign in with email <span className="h-px bg-outline-variant/30 flex-1" />
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="sign-in-email" className="block text-sm font-semibold mb-2">Email address</label>
              <input id="sign-in-email" type="email" autoComplete="username" required value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="w-full min-h-12 rounded-xl bg-surface border border-outline-variant/40 px-4 text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" />
            </div>
            <div>
              <label htmlFor="sign-in-password" className="block text-sm font-semibold mb-2">Password</label>
              <input id="sign-in-password" type="password" autoComplete="current-password" required value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="w-full min-h-12 rounded-xl bg-surface border border-outline-variant/40 px-4 text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" />
            </div>
            {error && <p role="alert" className="text-sm text-error">{error}</p>}
            <button type="submit" disabled={submitting}
              className="w-full min-h-12 rounded-xl liquid-gradient text-on-primary font-headline font-bold disabled:opacity-60 hover:shadow-lg transition-shadow">
              {submitting ? 'Signing in…' : 'Sign in with email'}
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}
