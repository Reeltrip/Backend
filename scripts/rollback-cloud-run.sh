#!/usr/bin/env bash
# 승인된 운영자가 지정한 이전 revision으로 실제 트래픽을 전환하는 수동 도구예요.
set -euo pipefail
if [[ $# -ne 3 ]]; then
  echo 'usage: bash scripts/rollback-cloud-run.sh PROJECT SERVICE PREVIOUS_REVISION' >&2; exit 2
fi
project=$1
service=$2
revision=$3
region=asia-northeast3
[[ $project =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]
[[ $service =~ ^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$ ]]
[[ $revision =~ ^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$ ]]
for required in gcloud jq curl; do
  command -v "$required" >/dev/null || { echo "$required 도구가 필요해요." >&2; exit 2; }
done
# 환경 변수·Secret 원문을 셸 변수나 로그에 담지 않아요.
before=$(gcloud run services describe "$service" --project="$project" --region="$region" --format=json |
  jq '{url: .status.url, traffic: [.status.traffic[]? | select(.percent > 0) | {revisionName, percent}]}')
url=$(jq -er '.url' <<< "$before")
[[ $url =~ ^https://[a-z0-9.-]+\.run\.app$ ]]
ready=$(gcloud run revisions describe "$revision" --project="$project" --region="$region" --format=json |
  jq -r --arg service "$service" --arg revision "$revision" '
    if .metadata.name == $revision and .metadata.labels["serving.knative.dev/service"] == $service
      and any(.status.conditions[]?; .type == "Ready" and .status == "True")
    then "ready" else "not-ready" end')
[[ $ready == ready ]] || { echo '대상 서비스의 READY revision인지 확인하지 못했어요.' >&2; exit 1; }
printf 'Before traffic: %s\n' "$(jq -c '.traffic' <<< "$before")"
gcloud run services update-traffic "$service" --project="$project" --region="$region" --to-revisions="$revision=100" --quiet
for attempt in {1..12}; do
  if body=$(curl --fail --silent --show-error --connect-timeout 3 --max-time 10 "$url/actuator/health") && jq -e '.status == "UP"' <<< "$body" >/dev/null; then
    printf 'Rollback health UP: %s\n' "$revision"
    exit 0
  fi
  sleep 3
done
echo '전환 후 health 확인 실패: 현재 트래픽과 로그를 운영자가 확인해요. 추가 자동 전환은 하지 않아요.' >&2
exit 1
