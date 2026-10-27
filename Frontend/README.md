# TrustTherapy frontend

React/Vite client for the versioned backend API. Node.js 24 is used for local checks.

## Local development

Set `VITE_API_BASE_URL` to the backend's `/api/v1` URL (default: `http://localhost:8080/api/v1`). This value is embedded in the browser build, so staging and production need a public HTTPS API URL. Do not put AWS, database, Razorpay secret, or other private credentials in `VITE_*` variables.

For the single-EC2 Sydney staging option, set `VITE_API_BASE_URL=https://<Elastic-IP>/api/v1`, `VITE_OAUTH_BASE_URL=https://trust-therapy-ai.netlify.app/api/v1`, and `NETLIFY_API_ORIGIN=https://<Elastic-IP>` in the Netlify build environment. The build then writes proxy rules for OAuth start/callback before the SPA fallback. Long-running chat requests stay on the direct HTTPS API URL because Netlify's proxy times out after 26 seconds. Set the backend's `CORS_ALLOWED_ORIGINS=https://trust-therapy-ai.netlify.app` and `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://trust-therapy-ai.netlify.app/api/v1/login/oauth2/code/google`. This route is an acceptance experiment; Google OAuth and cookie behavior must pass browser tests before release. The frontend remains visible when the backend is scheduled off from 00:00 to 07:00 India time, but API features will be unavailable.

Set `VITE_DEMO_SCHEDULE_ENABLED=true` in the Netlify build environment when the EC2 stop/start schedule is active. The app then shows a site-wide countdown from 23:00 to 00:00 IST, followed by a countdown to the planned 07:00 IST restart. Keep it disabled while the site still uses a backend without that schedule. The banner is based on the visitor's clock and does not replace a backend health check.

```bash
npm ci
npm run dev
npm run lint
npm test
npm run build
```

The client uses Google OAuth ticket exchange when the backend enables it, while retaining the legacy callback until the coordinated staging rollout. Chat uses the v2 SSE contract; payment creates a server-owned order before Razorpay Checkout. Usage and memory changes come from authenticated backend routes.

## Release checks still required

Run browser acceptance against the deployed staging backend for Google sign-in and expiry, FREE/PRO chat and usage limits, stream cancellation and recovery, memory conflicts/forget/reset, and Razorpay test-mode capture and webhook replay. Do not switch the public frontend API URL before the backend staging and restore gates in `Backend/docs/DEVELOPMENT_PLAN.md` pass.
