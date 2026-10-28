import { writeFileSync } from 'node:fs';

const rawOrigin = process.env.NETLIFY_API_ORIGIN?.trim();
const rules = [];

if (rawOrigin) {
  if (process.env.CONTEXT === 'production') {
    throw new Error('The staging API proxy must be limited to a Deploy Preview.');
  }
  const origin = new URL(rawOrigin);
  if (origin.protocol !== 'https:' || origin.username || origin.password ||
      origin.pathname !== '/' || origin.search || origin.hash) {
    throw new Error('NETLIFY_API_ORIGIN must be an HTTPS origin without a path, query, or credentials.');
  }

  rules.push(`/api/v1/oauth2/* ${origin.origin}/api/v1/oauth2/:splat 200`);
  rules.push(`/api/v1/login/oauth2/* ${origin.origin}/api/v1/login/oauth2/:splat 200`);
  rules.push(`/api/v1/payment/webhook ${origin.origin}/api/v1/payment/webhook 200`);
}

rules.push('/* /index.html 200');
writeFileSync(new URL('../dist/_redirects', import.meta.url), `${rules.join('\n')}\n`);
