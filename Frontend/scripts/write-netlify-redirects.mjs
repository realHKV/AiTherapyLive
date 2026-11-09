import { writeFileSync } from 'node:fs';

const rawOrigin = process.env.NETLIFY_API_ORIGIN?.trim();
const rules = [];

if (rawOrigin) {
  const origin = new URL(rawOrigin);
  if (origin.protocol !== 'https:' || origin.username || origin.password ||
      origin.pathname !== '/' || origin.search || origin.hash) {
    throw new Error('NETLIFY_API_ORIGIN must be an HTTPS origin without a path, query, or credentials.');
  }
  if (process.env.CONTEXT === 'production') {
    if (process.env.NETLIFY_PRODUCTION_PROXY_ENABLED !== 'true') {
      throw new Error('A production API proxy requires NETLIFY_PRODUCTION_PROXY_ENABLED=true.');
    }
    const apiBase = new URL(process.env.VITE_API_BASE_URL || '');
    const oauthBase = new URL(process.env.VITE_OAUTH_BASE_URL || '');
    const siteOrigin = new URL(process.env.URL || '');
    if (apiBase.origin !== origin.origin || apiBase.pathname !== '/api/v1' ||
        oauthBase.origin !== siteOrigin.origin || oauthBase.pathname !== '/api/v1' ||
        siteOrigin.protocol !== 'https:') {
      throw new Error('Production API, OAuth, and Netlify site URLs do not match the proxy configuration.');
    }
  }

  rules.push(`/api/v1/oauth2/* ${origin.origin}/api/v1/oauth2/:splat 200`);
  rules.push(`/api/v1/login/oauth2/* ${origin.origin}/api/v1/login/oauth2/:splat 200`);
  rules.push(`/api/v1/payment/webhook ${origin.origin}/api/v1/payment/webhook 200`);
}

rules.push('/* /index.html 200');
writeFileSync(new URL('../dist/_redirects', import.meta.url), `${rules.join('\n')}\n`);
if (process.env.CONTEXT === 'deploy-preview') {
  writeFileSync(new URL('../dist/_headers', import.meta.url), '/*\n  X-Robots-Tag: noindex, nofollow\n');
}
