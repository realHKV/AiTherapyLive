import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { analyticsAvailable, analyticsConsent, setAnalyticsConsent } from '../analytics/posthog';

export default function Privacy() {
  const [analyticsEnabled, setAnalyticsEnabled] = useState(analyticsConsent);
  return (
    <div className="bg-surface text-on-surface font-body min-h-screen selection:bg-primary-container selection:text-on-primary-container">
      {/* Simple Header */}
      <header className="fixed top-0 w-full z-50 glass-nav border-b border-outline-variant/10">
        <nav className="flex justify-between items-center px-6 py-4 w-full max-w-7xl mx-auto">
          <Link to="/" className="text-xl font-bold text-on-surface dark:text-white font-headline tracking-tight">
            TrustTherapy.ai
          </Link>
          <Link to="/" className="text-primary font-headline font-semibold hover:opacity-80 transition-all flex items-center gap-2">
            <span className="material-symbols-outlined">arrow_back</span>
            Back to Home
          </Link>
        </nav>
      </header>

      <main className="pt-32 pb-20 px-6 max-w-4xl mx-auto">
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-primary/10 border border-primary/20 mb-6">
          <span className="w-2 h-2 rounded-full bg-primary animate-pulse"></span>
          <span className="text-xs font-bold uppercase tracking-widest text-primary font-label">Privacy Policy</span>
        </div>
        
        <h1 className="font-headline text-4xl md:text-6xl font-extrabold tracking-tight mb-8">
          How your <span className="text-primary italic">data is handled.</span>
        </h1>

        <p className="text-lg text-on-surface-variant mb-12 font-body leading-relaxed">
          Conversations can contain sensitive information. This page describes the data handling built into the app.
        </p>

        <div className="space-y-12">
          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">1. Storage and processing</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              The application encrypts message content and memory text before saving them in its database.
              The service decrypts relevant content to build AI requests and display your history. This is application-level
              encryption at rest, not end-to-end encryption.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">2. AI processing</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              Relevant conversation content is sent to the configured AI provider so it can generate a response.
              Do not assume that removing account identifiers makes the conversation anonymous; text you enter may itself identify you.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">3. Zero Data Selling</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              We do not sell conversation content to advertisers. Processing a conversation requires the AI provider
              described above and the service providers that host the app and its data.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">4. Authentication & Access</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              Google sign-in is available. The backend also supports account-password login; those passwords are
              handled by the app's authentication service. Signed-in requests use access and refresh tokens.
            </p>
          </section>

          <section className="p-8 rounded-3xl bg-surface-container-low border border-outline-variant/10">
            <h2 className="font-headline text-2xl font-bold mb-4 text-primary">5. Right to Erase</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              You can forget a saved memory, reset your AI profile and memory context, or request account deletion
              from the app. Account deletion removes active application records. Backup and service-provider retention
              may follow separate schedules.
            </p>
          </section>
          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">6. Optional public page analytics</h2>
            <p className="text-on-surface-variant leading-relaxed">
              If you opt in on the landing page, PostHog records a landing page visit without your account identity. Analytics is off by default.
              Chat text, profile details, sign-in identity, and session replay are not sent to PostHog. You can turn this off
              here or from the landing page. The integration remains inactive until a PostHog project is configured.
            </p>
            {analyticsAvailable && (
              <label className="inline-flex items-center gap-3 mt-4 text-on-surface cursor-pointer">
                <input type="checkbox" checked={analyticsEnabled} onChange={event => { setAnalyticsConsent(event.target.checked); setAnalyticsEnabled(event.target.checked); }} />
                Allow public landing page analytics
              </label>
            )}
          </section>
        </div>

        <footer className="mt-20 pt-10 border-t border-outline-variant/10 text-center">
          <p className="text-sm text-on-surface-variant/60">
            Last updated: October 26, 2026<br />
            Questions? Contact us at imnotharsh14@gmail.com
          </p>
        </footer>
      </main>
    </div>
  );
}
