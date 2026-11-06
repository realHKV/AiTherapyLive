#!/usr/bin/env bash
set -euo pipefail

# Run on the Sydney EC2 host through SSM.
image="${1:?Usage: private-ec2-start.sh IMAGE}"
parameter_name="/aitherapy/demo/runtime-env"
webhook_parameter_name="/aitherapy/demo/razorpay-webhook-secret"
runtime_dir="/etc/aitherapy"
runtime_file="$runtime_dir/runtime.env"
parameter_file="$(mktemp)"
webhook_parameter_file="$(mktemp)"
webhook_error_file="$(mktemp)"
previous_file=""
trap 'rm -f "$parameter_file" "$webhook_parameter_file" "$webhook_error_file"; if [ -n "$previous_file" ]; then rm -f "$previous_file"; fi' EXIT

start_container() {
  local selected_image="$1"
  docker run -d --name aitherapy-demo-app --network host \
    --env-file "$runtime_file" --restart unless-stopped \
    --memory 1300m --memory-swap 1800m \
    --log-opt max-size=10m --log-opt max-file=3 \
    "$selected_image" serve >/dev/null
}

wait_for_readiness() {
  for attempt in {1..90}; do
    if curl --fail --silent http://127.0.0.1:8080/api/v1/actuator/health/readiness \
        | grep -q '"status":"UP"'; then
      return 0
    fi
    if [ "$(docker inspect -f '{{.State.Status}}' aitherapy-demo-app)" != running ]; then
      return 1
    fi
    sleep 2
  done
  return 1
}

docker image inspect "$image" >/dev/null
install -d -m 0700 "$runtime_dir"
previous_image="$(docker inspect -f '{{.Image}}' aitherapy-demo-app 2>/dev/null || true)"
if [ -n "$previous_image" ] && [ -f "$runtime_file" ]; then
  previous_file="$(mktemp "$runtime_dir/runtime.previous.XXXXXX")"
  cp "$runtime_file" "$previous_file"
  chmod 0600 "$previous_file"
fi
chmod 0600 "$parameter_file"
aws ssm get-parameter --region ap-southeast-2 --name "$parameter_name" \
  --with-decryption --output json > "$parameter_file"
if ! aws ssm get-parameter --region ap-southeast-2 --name "$webhook_parameter_name" \
    --with-decryption --output json > "$webhook_parameter_file" 2> "$webhook_error_file"; then
  if grep -q '(ParameterNotFound)' "$webhook_error_file"; then
    : > "$webhook_parameter_file"
  else
    cat "$webhook_error_file" >&2
    exit 1
  fi
fi

python3 - "$parameter_file" "$webhook_parameter_file" "$runtime_file" <<'PY'
import json
import os
import sys
import tempfile

source, webhook_source, destination = sys.argv[1:]
with open(source, encoding="utf-8") as handle:
    values = json.load(handle)["Parameter"]["Value"]
settings = json.loads(values)
if os.path.getsize(webhook_source):
    with open(webhook_source, encoding="utf-8") as handle:
        webhook_secret = json.load(handle)["Parameter"]["Value"]
    if not webhook_secret or any(char in webhook_secret for char in "\r\n\0"):
        raise SystemExit("Invalid separate Razorpay webhook secret")
    settings["RAZORPAY_WEBHOOK_SECRET"] = webhook_secret
required = {
    "DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "REDIS_HOST",
    "REDIS_PASSWORD", "JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY",
    "ENCRYPTION_MASTER_KEY", "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
    "RAZORPAY_KEY_ID", "RAZORPAY_KEY_SECRET", "RAZORPAY_WEBHOOK_SECRET",
}
if not required.issubset(settings):
    raise SystemExit("Incomplete private runtime parameter")
if settings.get("SERVER_ADDRESS") != "127.0.0.1":
    raise SystemExit("Private backend must bind to loopback")
if not settings["DATABASE_URL"].startswith("jdbc:postgresql://") or \
        "sslmode=verify-full" not in settings["DATABASE_URL"]:
    raise SystemExit("Verified PostgreSQL TLS is required")
for key, value in settings.items():
    if not key.replace("_", "").isalnum() or not isinstance(value, str) or \
            any(char in value for char in "\r\n\0"):
        raise SystemExit("Invalid private runtime setting")
descriptor, temporary = tempfile.mkstemp(dir=os.path.dirname(destination), prefix="runtime.")
try:
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        for key, value in sorted(settings.items()):
            handle.write(f"{key}={value}\n")
    os.chmod(temporary, 0o600)
    os.replace(temporary, destination)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY

if [ -n "$previous_image" ]; then
  docker rm -f aitherapy-demo-app >/dev/null
fi
if start_container "$image" && wait_for_readiness; then
  echo "Private backend readiness UP"
  exit 0
fi

echo "Private backend did not reach readiness" >&2
docker rm -f aitherapy-demo-app >/dev/null 2>&1 || true
if [ -n "$previous_image" ] && [ -n "$previous_file" ]; then
  mv "$previous_file" "$runtime_file"
  previous_file=""
  if start_container "$previous_image" && wait_for_readiness; then
    echo "Previous private backend restored and ready" >&2
    exit 1
  fi
  echo "Previous private backend restoration failed" >&2
fi
exit 1
