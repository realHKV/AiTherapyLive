#!/usr/bin/env bash
set -Eeuo pipefail

# Public checks only. Run after App Runner reports the deployed revision ready.
base_url="${1:-}"
if [[ ! "$base_url" =~ ^https://[a-z0-9.-]+\.awsapprunner\.com/?$ ]]; then
  echo 'Usage: demo-smoke.sh https://<service>.awsapprunner.com' >&2
  exit 2
fi
base_url="${base_url%/}"
origin='https://trust-therapy-ai.netlify.app'
body=$(mktemp)
headers=$(mktemp)
trap 'rm -f "$body" "$headers"' EXIT

for endpoint in liveness readiness; do
  curl --fail --silent --show-error --max-time 20 --retry 2 \
    --output "$body" "$base_url/api/v1/actuator/health/$endpoint"
  if ! jq -e '.status == "UP"' "$body" >/dev/null; then
    echo "$endpoint did not report UP" >&2
    exit 1
  fi
  echo "$endpoint: UP"
done

curl --silent --show-error --max-time 20 --dump-header "$headers" \
  --output /dev/null --request OPTIONS \
  --header "Origin: $origin" \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: Content-Type' \
  "$base_url/api/v1/auth/login"
tr -d '\r' < "$headers" | tr '[:upper:]' '[:lower:]' > "$body"
if ! grep -Fxq "access-control-allow-origin: $origin" "$body" ||
   ! grep -Fxq 'access-control-allow-credentials: true' "$body"; then
  echo 'Netlify CORS preflight failed' >&2
  exit 1
fi
echo 'Netlify CORS preflight: allowed'

curl --silent --show-error --max-time 20 --dump-header "$headers" \
  --output /dev/null --request OPTIONS \
  --header 'Origin: https://unexpected.example' \
  --header 'Access-Control-Request-Method: POST' \
  "$base_url/api/v1/auth/login"
tr -d '\r' < "$headers" | tr '[:upper:]' '[:lower:]' > "$body"
if grep -q '^access-control-allow-origin:' "$body"; then
  echo 'Unexpected origin received CORS access' >&2
  exit 1
fi
echo 'Unexpected CORS origin: denied'
