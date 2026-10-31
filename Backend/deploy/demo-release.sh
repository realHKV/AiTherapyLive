#!/usr/bin/env bash
set -Eeuo pipefail

# Manual GitHub Actions job: verified Docker Hub digest -> ECR -> Flyway -> App Runner.
DEPLOY_MODE="${DEPLOY_MODE:-deploy}"
if [[ "$DEPLOY_MODE" != bootstrap && "$DEPLOY_MODE" != deploy ]]; then echo 'Invalid deployment mode' >&2; exit 2; fi
required=(AWS_REGION ECR_REPOSITORY_URL DOCKERHUB_USERNAME DOCKERHUB_TOKEN DATABASE_URL_ARN MIGRATION_DATABASE_USER_ARN MIGRATION_DATABASE_PASSWORD_ARN RELEASE_MANIFEST GH_TOKEN)
if [[ "$DEPLOY_MODE" == deploy ]]; then required+=(APP_RUNNER_SERVICE_ARN); fi
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then echo "Missing deployment setting: $name" >&2; exit 2; fi
done
if [[ "$AWS_REGION" != ap-south-1 || ! "$RELEASE_MANIFEST" =~ ^Backend/docs/releases/[A-Za-z0-9._-]+\.json$ ]]; then
  echo 'Invalid region or release manifest path' >&2; exit 2
fi
if [[ ! -f "$RELEASE_MANIFEST" ]]; then echo 'Release manifest missing' >&2; exit 2; fi
source_image=$(jq -er '.image' "$RELEASE_MANIFEST")
source_digest=$(jq -er '.digest' "$RELEASE_MANIFEST")
source_commit=$(jq -er '.commit' "$RELEASE_MANIFEST")
if [[ "$source_image" != realhkv/aitherapy-backend || ! "$source_digest" =~ ^sha256:[0-9a-f]{64}$ || ! "$source_commit" =~ ^[0-9a-f]{40}$ ]]; then
  echo 'Release manifest identity invalid' >&2; exit 2
fi
if [[ "$(jq -er '.platform' "$RELEASE_MANIFEST")" != linux/amd64 ]]; then
  echo 'Only the tested linux/amd64 image is supported' >&2; exit 2
fi
test_run=$(gh api "repos/realHKV/AiTherapyLive/actions/workflows/backend-tests.yml/runs?head_sha=$source_commit&per_page=100" \
  | jq -er --arg sha "$source_commit" '[.workflow_runs[] | select(.head_sha == $sha and .conclusion == "success")][0].id')
release_run=$(gh api "repos/realHKV/AiTherapyLive/actions/workflows/backend-release.yml/runs?head_sha=$source_commit&per_page=100" \
  | jq -er --arg sha "$source_commit" '[.workflow_runs[] | select(.head_sha == $sha and .conclusion == "success")][0].id')
if [[ ! "$test_run" =~ ^[0-9]+$ || ! "$release_run" =~ ^[0-9]+$ ]]; then
  echo 'Source CI or publication gate missing' >&2; exit 2
fi
artifact_dir=$(mktemp -d)
trap 'rm -rf "$artifact_dir"' EXIT
gh run download "$release_run" --repo realHKV/AiTherapyLive --name "backend-image-release-$source_commit" --dir "$artifact_dir" >/dev/null
if ! jq -e --arg image "$source_image" --arg digest "$source_digest" --arg commit "$source_commit" \
  '.image == $image and .digest == $digest and .commit == $commit and .platform == "linux/amd64"' \
  "$artifact_dir/backend-image-release.json" >/dev/null; then
  echo 'Committed manifest does not match the published release artifact' >&2; exit 2
fi

echo "$DOCKERHUB_TOKEN" | docker login --username "$DOCKERHUB_USERNAME" --password-stdin >/dev/null
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${ECR_REPOSITORY_URL%%/*}" >/dev/null
docker pull --platform linux/amd64 "$source_image@$source_digest" >/dev/null
tag="source-${source_commit:0:12}-${source_digest:7:12}"
destination="$ECR_REPOSITORY_URL:$tag"
docker tag "$source_image@$source_digest" "$destination"
if ! aws ecr describe-images --region "$AWS_REGION" --repository-name "${ECR_REPOSITORY_URL##*/}" --image-ids "imageTag=$tag" >/dev/null 2>&1; then
  docker push "$destination" >/dev/null
fi
ecr_digest=$(aws ecr describe-images --region "$AWS_REGION" --repository-name "${ECR_REPOSITORY_URL##*/}" --image-ids "imageTag=$tag" --query 'imageDetails[0].imageDigest' --output text)
if [[ ! "$ecr_digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then echo 'ECR did not return a digest' >&2; exit 1; fi
destination="$ECR_REPOSITORY_URL@$ecr_digest"
echo "Promoting tested source commit $source_commit to ECR digest $ecr_digest"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then printf 'ecr_digest=%s\n' "$ecr_digest" >> "$GITHUB_OUTPUT"; fi

get_secret() {
  local arn="$1"
  case "$arn" in
    arn:aws:secretsmanager:ap-south-1:*) aws secretsmanager get-secret-value --region "$AWS_REGION" --secret-id "$arn" --query SecretString --output text ;;
    arn:aws:ssm:ap-south-1:*) aws ssm get-parameter --region "$AWS_REGION" --name "$arn" --with-decryption --query Parameter.Value --output text ;;
    *) echo 'Migration secret ARN invalid' >&2; return 2 ;;
  esac
}
export DATABASE_URL DATABASE_USER DATABASE_PASSWORD
DATABASE_URL=$(get_secret "$DATABASE_URL_ARN")
DATABASE_USER=$(get_secret "$MIGRATION_DATABASE_USER_ARN")
DATABASE_PASSWORD=$(get_secret "$MIGRATION_DATABASE_PASSWORD_ARN")
if [[ -z "$DATABASE_URL" || -z "$DATABASE_USER" || -z "$DATABASE_PASSWORD" ]]; then
  echo 'Migration settings are empty' >&2; exit 2
fi
docker run --rm --platform linux/amd64 --env DATABASE_URL --env DATABASE_USER --env DATABASE_PASSWORD "$destination" migrate
unset DATABASE_URL DATABASE_USER DATABASE_PASSWORD

if [[ "$DEPLOY_MODE" == bootstrap ]]; then
  echo "Bootstrap migration passed. Set image_digest=$ecr_digest and create_service=true in Terraform, then apply."
else
  bash Backend/deploy/demo-update.sh "$APP_RUNNER_SERVICE_ARN" "$destination"
fi
