#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
env_file="$script_dir/.env"

if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file. Copy config/local.env.example and supply local credentials." >&2
  exit 1
fi

while IFS='=' read -r key value || [[ -n "$key" ]]; do
  case "$key" in
    ''|'#'*) ;;
    [A-Z_][A-Z0-9_]*) export "$key=$value" ;;
  esac
done < "$env_file"

for required_var in DATABASE_URL DATABASE_USER DATABASE_PASSWORD REDIS_HOST REDIS_PASSWORD ENCRYPTION_MASTER_KEY JWT_PRIVATE_KEY JWT_PUBLIC_KEY; do
  if [[ -z "${!required_var:-}" ]]; then
    echo "Missing required setting: $required_var" >&2
    exit 1
  fi
done

echo "Starting with the database and Redis targets configured in .env."
echo "Warning: Flyway is enabled and may apply pending migrations to that database."
cd "$script_dir"
exec bash "$script_dir/mvnw" spring-boot:run
