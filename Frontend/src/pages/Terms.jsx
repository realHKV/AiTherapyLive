import React from 'react';
import { Link } from 'react-router-dom';

export default function Terms() {
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
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-secondary/10 border border-secondary/20 mb-6">
          <span className="w-2 h-2 rounded-full bg-secondary"></span>
          <span className="text-xs font-bold uppercase tracking-widest text-secondary font-label">Terms of Service</span>
        </div>
        
        <h1 className="font-headline text-4xl md:text-6xl font-extrabold tracking-tight mb-8">
          Our Agreement for <span className="text-primary italic">Betterment.</span>
        </h1>

        <div className="flex gap-4 p-6 rounded-3xl border-l-4 border-primary bg-primary/5 mb-12">
          <span className="material-symbols-outlined text-primary text-3xl flex-shrink-0">warning</span>
          <p className="font-body text-base text-on-surface-variant leading-relaxed">
            <span className="font-bold text-on-surface uppercase block mb-1">Medical Disclaimer</span>
            TrustTherapy.ai is an AI-powered conversational tool designed for emotional support and self-reflection. 
            It is NOT a medical device, a clinical therapy service, or a replacement for licensed mental health professionals. 
            By using this service, you acknowledge that you are interacting with an Artificial Intelligence.
          </p>
        </div>

        <div className="space-y-12">
          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">1. Acceptance of Terms</h2>
            <p className="text-on-surface-variant leading-relaxed">
              By accessing TrustTherapy.ai, you agree to be bound by these Terms of Service. If you do not agree to 
              any part of these terms, you must not use the service.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">2. Crisis Protocol</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              If you are experiencing thoughts of self-harm or a medical emergency, you must immediately contact 
              emergency services or a dedicated crisis hotline. Our AI is programmed to provide resources in these 
              situations, but it cannot take physical action or provide emergency clinical intervention.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">3. User Conduct</h2>
            <p className="text-on-surface-variant leading-relaxed">
              You agree to use TrustTherapy.ai for lawful purposes only. You must not attempt to "jailbreak," 
              exploit, or use the AI to generate harmful, illegal, or abusive content. We reserve the right 
              to terminate access for users who violate these principles.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">4. Subscriptions & Payments</h2>
            <p className="text-on-surface-variant leading-relaxed mb-4">
              Our "Pro" tier is a recurring subscription processed via Razorpay. You can cancel at any time through 
              the app. Refunds are handled on a case-by-case basis. We reserve the right to change our pricing 
              with reasonable notice to active subscribers.
            </p>
          </section>

          <section>
            <h2 className="font-headline text-2xl font-bold mb-4 text-on-surface">5. Limitation of Liability</h2>
            <p className="text-on-surface-variant leading-relaxed">
              TrustTherapy.ai and its creators are not liable for any decisions made or actions taken by you 
              based on conversations with the AI. The AI's responses are generated based on probabilistic 
              models and should be treated as suggestions for reflection, not objective truth or medical advice.
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
