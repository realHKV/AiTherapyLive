#!/usr/bin/env bash
set -euo pipefail

image="${1:?Usage: smoke.sh IMAGE}"
workdir="$(mktemp -d)"
cleanup() {
  docker rm -f aitherapy-smoke-app aitherapy-smoke-postgres aitherapy-smoke-redis >/dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

docker run -d --name aitherapy-smoke-postgres --network host \
  -e POSTGRES_DB=therapyai -e POSTGRES_USER=therapyai_user -e POSTGRES_PASSWORD=smoke_only \
  postgres:16 >/dev/null
docker run -d --name aitherapy-smoke-redis --network host redis:7 >/dev/null

for attempt in {1..30}; do
  if docker exec aitherapy-smoke-postgres pg_isready -U therapyai_user -d therapyai >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec aitherapy-smoke-postgres pg_isready -U therapyai_user -d therapyai >/dev/null

common_env=(
  -e DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/therapyai
  -e DATABASE_USER=therapyai_user
  -e DATABASE_PASSWORD=smoke_only
)
docker run --rm --network host "${common_env[@]}" "$image" migrate
docker run --rm --network host "${common_env[@]}" "$image" migrate
applied="$(docker exec aitherapy-smoke-postgres psql -U therapyai_user -d therapyai -Atc \
  "select count(*) from flyway_schema_history where success = true")"
test "$applied" -ge 9

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$workdir/private.pem" 2>/dev/null
openssl pkcs8 -topk8 -nocrypt -in "$workdir/private.pem" -outform DER -out "$workdir/private.der"
openssl pkey -in "$workdir/private.pem" -pubout -outform DER -out "$workdir/public.der"
jwt_private="$(base64 -w0 "$workdir/private.der")"
jwt_public="$(base64 -w0 "$workdir/public.der")"
encryption_key="$(openssl rand -base64 32 | tr -d '\n')"

docker run -d --name aitherapy-smoke-app --network host "${common_env[@]}" \
  -e REDIS_HOST=127.0.0.1 -e SPRING_FLYWAY_ENABLED=false \
  -e JWT_PRIVATE_KEY="$jwt_private" -e JWT_PUBLIC_KEY="$jwt_public" \
  -e ENCRYPTION_MASTER_KEY="$encryption_key" "$image" >/dev/null

ready=0
for attempt in {1..90}; do
  if curl --fail --silent http://127.0.0.1:8080/api/v1/actuator/health/readiness | grep -q '"status":"UP"'; then
    ready=1
    break
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' aitherapy-smoke-app)" != true ]; then break; fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  docker logs --tail 100 aitherapy-smoke-app
  exit 1
fi
docker stop --time 25 aitherapy-smoke-app >/dev/null
test "$(docker inspect -f '{{.State.ExitCode}}' aitherapy-smoke-app)" -eq 143 || \
  test "$(docker inspect -f '{{.State.ExitCode}}' aitherapy-smoke-app)" -eq 0
