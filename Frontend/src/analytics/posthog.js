// Public landing analytics only. Never capture chat, profiles, identities, or page URLs.
const consentKey = 'trusttherapy_public_analytics_consent';
const projectKey = import.meta.env.VITE_POSTHOG_KEY;
const region = import.meta.env.VITE_POSTHOG_REGION;
const host = region === 'EU' ? 'https://eu.i.posthog.com' : region === 'US' ? 'https://us.i.posthog.com' : null;
let initialized = false;

export const analyticsAvailable = Boolean(projectKey && host);
export const analyticsConsent = () => localStorage.getItem(consentKey) === 'yes';

function loadPostHog() {
  if (!analyticsAvailable || initialized) return;
  initialized = true;
  const client = [];
  client._i = [];
  client.people = [];
  client.__SV = 1;
  client.toString = () => 'posthog (stub)';
  client.people.toString = () => 'posthog.people (stub)';
  client.init = (key, config) => client._i.push([key, config, 'posthog']);
  for (const method of ['capture', 'opt_in_capturing', 'opt_out_capturing']) {
    client[method] = (...args) => client.push([method, ...args]);
  }
  // The official PostHog browser script consumes this queue when it loads.
  window.posthog = client;
  client.init(projectKey, {
    api_host: host,
    autocapture: false,
    capture_pageview: false,
    capture_pageleave: false,
    disable_session_recording: true,
    opt_out_capturing_by_default: true,
    person_profiles: 'never',
    persistence: 'memory',
    mask_all_text: true,
  });
  const script = document.createElement('script');
  script.async = true;
  script.crossOrigin = 'anonymous';
  script.src = `${host.replace('.i.posthog.com', '-assets.i.posthog.com')}/static/array.js`;
  script.onerror = () => { initialized = false; window.posthog = undefined; };
  document.head.appendChild(script);
  client.opt_in_capturing({ captureEventName: false });
}

export function recordPublicLandingVisit() {
  if (!analyticsConsent() || !analyticsAvailable || window.location.pathname !== '/' || window.location.search) return;
  loadPostHog();
  window.posthog?.capture('public_landing_viewed', {});
}

export function setAnalyticsConsent(allowed) {
  localStorage.setItem(consentKey, allowed ? 'yes' : 'no');
  if (allowed) recordPublicLandingVisit();
  else window.posthog?.opt_out_capturing();
}
