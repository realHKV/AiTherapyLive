#!/usr/bin/env bash
set -euo pipefail

# Run on the Sydney EC2 host through SSM. The security group has no ingress.
image="${1:?Usage: private-ec2-start.sh IMAGE}"
parameter_name="/aitherapy/demo/runtime-env"
runtime_dir="/etc/aitherapy"
runtime_file="$runtime_dir/runtime.env"
parameter_file="$(mktemp)"
trap 'rm -f "$parameter_file"' EXIT

docker image inspect "$image" >/dev/null
install -d -m 0700 "$runtime_dir"
chmod 0600 "$parameter_file"
aws ssm get-parameter --region ap-southeast-2 --name "$parameter_name" \
  --with-decryption --output json > "$parameter_file"

python3 - "$parameter_file" "$runtime_file" <<'PY'
import json
import os
import sys
import tempfile

source, destination = sys.argv[1:]
with open(source, encoding="utf-8") as handle:
    values = json.load(handle)["Parameter"]["Value"]
settings = json.loads(values)
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

docker rm -f aitherapy-demo-app >/dev/null 2>&1 || true
docker run -d --name aitherapy-demo-app --network host \
  --env-file "$runtime_file" --restart unless-stopped \
  --memory 1300m --memory-swap 1800m \
  --log-opt max-size=10m --log-opt max-file=3 \
  "$image" serve >/dev/null

for attempt in {1..90}; do
  if curl --fail --silent http://127.0.0.1:8080/api/v1/actuator/health/readiness \
      | grep -q '"status":"UP"'; then
    echo "Private backend readiness UP"
    exit 0
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' aitherapy-demo-app)" != true ]; then
    echo "Private backend exited before readiness" >&2
    exit 1
  fi
  sleep 2
done

echo "Private backend did not reach readiness" >&2
exit 1
