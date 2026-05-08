import React from 'react';
import { Link } from 'react-router-dom';

export default function Privacy() {
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
          Your Privacy is <span className="text-primary italic">Absolute.</span>
        </h1>

        <p className="text-lg text-on-surface-variant mb-12 font-body leading-relaxed">
          At TrustTherapy.ai, we believe mental health data is the most sensitive information a person can share. 
          Our platform is built from the ground up to ensure your words remain your own. This policy explains how we 
          protect your data and why we built it this way.
        </p>

        <div className="space-y-12">
          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">1. End-to-End Encryption</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              Every message you send, every session summary generated, and every memory extracted is encrypted at rest. 
              We use industry-standard AES-256 encryption. Our internal systems are designed such that no human employee 
              at TrustTherapy.ai has the technical ability to read your private conversations.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">2. Data Anonymization</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              When our AI models (powered by Google Gemini) process your sessions, we strip away identifying metadata. 
              The AI "sees" the content of your conversation to provide support, but it does not "know" who you are 
              in the context of the world. Your account identity and your therapeutic content are kept in separate 
              encrypted silos.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">3. Zero Data Selling</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              We do not sell, rent, or trade your data. Our business model is based entirely on our optional Pro 
              subscriptions. We have no incentive—and no intention—to ever monetize your emotional journey through 
              advertising or third-party data sharing.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">4. Authentication & Access</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              We use Google OAuth 2.0 for all logins. This means we never see or store your passwords. 
              Security is handled by Google's world-class infrastructure, ensuring your account remains 
              protected by the same standards as your primary email.
            </p>
          </section>

          <section className="p-8 rounded-3xl bg-surface-container-low border border-outline-variant/10">
            <h2 className="font-headline text-2xl font-bold mb-4 text-primary">5. Right to Erase</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              You have complete control. At any time, you can delete your profile data or your entire account 
              directly from the settings. When you click "Delete," our system performs a permanent wipe of all 
              associated database records across all tables. There are no "soft deletes" for your memories.
            </p>
          </section>
        </div>

        <footer className="mt-20 pt-10 border-t border-outline-variant/10 text-center">
          <p className="text-sm text-on-surface-variant/60">
            Last updated: April 25, 2026<br />
            Questions? Contact us at imnotharsh14@gmail.com
          </p>
        </footer>
      </main>
    </div>
  );
}
