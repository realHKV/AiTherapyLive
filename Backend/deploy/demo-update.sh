#!/usr/bin/env bash
set -Eeuo pipefail
service_arn="${1:?App Runner service ARN required}"
image="${2:?ECR digest image required}"
if [[ ! "$service_arn" =~ ^arn:aws:apprunner:ap-south-1:[0-9]{12}:service/aitherapy-demo-api/ || ! "$image" =~ ^[0-9]{12}\.dkr\.ecr\.ap-south-1\.amazonaws\.com/aitherapy-backend-demo@sha256:[0-9a-f]{64}$ ]]; then
  echo 'Unexpected service or image identity' >&2; exit 2
fi
old_config=$(mktemp)
new_config=$(mktemp)
trap 'rm -f "$old_config" "$new_config"' EXIT
aws apprunner describe-service --service-arn "$service_arn" --query Service.SourceConfiguration --output json > "$old_config"
jq --arg image "$image" '.ImageRepository.ImageIdentifier = $image' "$old_config" > "$new_config"
operation_id=$(aws apprunner update-service --service-arn "$service_arn" --source-configuration "file://$new_config" --query OperationId --output text)
if [[ -z "$operation_id" || "$operation_id" == None ]]; then echo 'App Runner returned no operation ID' >&2; exit 1; fi

wait_operation() {
  local id="$1" status
  for _ in {1..60}; do
    status=$(aws apprunner list-operations --service-arn "$service_arn" --query "OperationSummaryList[?Id=='$id'].Status | [0]" --output text)
    case "$status" in
      SUCCEEDED) return 0 ;;
      FAILED|ROLLBACK_FAILED|ROLLBACK_SUCCEEDED) return 1 ;;
    esac
    sleep 20
  done
  echo 'App Runner operation timed out' >&2
  return 1
}

if wait_operation "$operation_id"; then
  service_url=$(aws apprunner describe-service --service-arn "$service_arn" --query Service.ServiceUrl --output text)
  if [[ "$service_url" =~ ^[a-z0-9.-]+\.awsapprunner\.com$ ]] && curl -fsS --retry 5 --retry-delay 3 --max-time 15 "https://$service_url/api/v1/actuator/health/readiness" >/dev/null; then
    echo "Deployed $image; readiness passed at https://$service_url/api/v1/actuator/health/readiness"
    exit 0
  fi
  echo 'New revision did not pass readiness' >&2
else
  echo 'App Runner deployment failed' >&2
fi

echo 'Restoring the previous App Runner source configuration' >&2
rollback_id=$(aws apprunner update-service --service-arn "$service_arn" --source-configuration "file://$old_config" --query OperationId --output text)
if ! wait_operation "$rollback_id"; then echo 'Rollback failed; operator action required' >&2; exit 1; fi
echo 'Previous image restored; database migrations were not rolled back' >&2
exit 1
