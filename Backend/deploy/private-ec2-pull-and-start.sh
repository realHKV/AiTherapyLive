#!/usr/bin/env bash
set -euo pipefail

# Run on the Sydney SSM-only host after a read-only Docker Hub token has been
# stored as /aitherapy/demo/dockerhub-read-token (SecureString).
image="${1:?Usage: private-ec2-pull-and-start.sh IMAGE@sha256:DIGEST [DOCKER_ID]}"
docker_id="${2:-realhkv}"
if [[ ! "$image" =~ ^realhkv/aitherapy-backend@sha256:[0-9a-f]{64}$ ]]; then
  echo "Expected an immutable AiTherapy Docker Hub digest" >&2
  exit 2
fi
if [[ ! "$docker_id" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
  echo "Invalid Docker ID" >&2
  exit 2
fi

docker_config="$(mktemp -d)"
chmod 0700 "$docker_config"
trap 'rm -rf -- "$docker_config"' EXIT
export DOCKER_CONFIG="$docker_config"

aws ssm get-parameter --region ap-southeast-2 \
  --name /aitherapy/demo/dockerhub-read-token --with-decryption \
  --query Parameter.Value --output text \
  | docker login --username "$docker_id" --password-stdin >/dev/null

docker pull "$image" >/dev/null
if [ "$(docker image inspect -f '{{.Os}}/{{.Architecture}}' "$image")" != linux/amd64 ]; then
  echo "Unexpected image platform" >&2
  exit 1
fi

bash "$(dirname "$0")/private-ec2-start.sh" "$image"
echo "Immutable private backend image ready: $image"
