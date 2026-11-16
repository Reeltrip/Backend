#!/usr/bin/env bash
set -euo pipefail
: "${GCP_PROJECT_ID:?}" "${GCP_REGION:?}" "${AR_REPOSITORY:?}" "${CLOUD_RUN_SERVICE:?}" "${CLOUD_RUN_CONTAINER:?}" "${IMAGE_SHA:?}"
[[ $GCP_PROJECT_ID =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ && $GCP_REGION == asia-northeast3 ]]
[[ $AR_REPOSITORY =~ ^[a-z][a-z0-9-]{0,61}[a-z0-9]$ ]]
[[ $CLOUD_RUN_SERVICE =~ ^[a-z][a-z0-9-]{0,47}[a-z0-9]$ && $CLOUD_RUN_CONTAINER =~ ^[a-z][a-z0-9-]{0,47}[a-z0-9]$ ]]
[[ $IMAGE_SHA =~ ^[a-f0-9]{40}$ ]]
registry="$GCP_REGION-docker.pkg.dev"
image="$registry/$GCP_PROJECT_ID/$AR_REPOSITORY/backend:$IMAGE_SHA"
tag="verify-${IMAGE_SHA:0:12}"

# 첫 서비스·IAM·Secret·SQL 연결은 #27에서 승인한 설정을 먼저 준비해요.
before=$(gcloud run services describe "$CLOUD_RUN_SERVICE" --project="$GCP_PROJECT_ID" --region="$GCP_REGION" --format=json |
  jq '{spec: {template: {spec: {containers: [.spec.template.spec.containers[] | {name}]}}}, status: {traffic: .status.traffic}}')
jq -e --arg name "$CLOUD_RUN_CONTAINER" '.spec.template.spec.containers[] | select(.name == $name)' <<< "$before" >/dev/null
previous=$(jq -c '.status.traffic | map({revisionName,percent}) | map(select(.percent > 0))' <<< "$before")
gcloud auth configure-docker "$registry" --quiet
docker tag "reeltrip-release:$IMAGE_SHA" "$image"
docker push "$image"
digest=$(gcloud artifacts docker images describe "$image" --project="$GCP_PROJECT_ID" --format='value(image_summary.digest)')
[[ $digest =~ ^sha256:[a-f0-9]{64}$ ]]
pinned="${image%:*}@$digest"
gcloud run deploy "$CLOUD_RUN_SERVICE" --project="$GCP_PROJECT_ID" --region="$GCP_REGION" \
  --revision-suffix="sha-${IMAGE_SHA:0:12}" --no-traffic --tag="$tag" --quiet \
  --container="$CLOUD_RUN_CONTAINER" --image="$pinned"
after=$(gcloud run services describe "$CLOUD_RUN_SERVICE" --project="$GCP_PROJECT_ID" --region="$GCP_REGION" --format=json |
  jq '{status: {traffic: .status.traffic}}')
revision=$(jq -er --arg tag "$tag" '.status.traffic[] | select(.tag == $tag) | .revisionName' <<< "$after")
url=$(jq -er --arg tag "$tag" '.status.traffic[] | select(.tag == $tag) | .url' <<< "$after")
[[ $revision == "$CLOUD_RUN_SERVICE-sha-${IMAGE_SHA:0:12}" ]]
[[ $url =~ ^https://[a-z0-9.-]+\.run\.app$ ]]
healthy=false
for attempt in {1..12}; do
  if body=$(curl --fail --silent --show-error --connect-timeout 3 --max-time 10 "$url/actuator/health") && jq -e '.status == "UP"' <<< "$body" >/dev/null; then
    healthy=true
    break
  fi
  sleep 3
done
if [[ $healthy != true ]]; then
  echo '후보 revision의 health 실패: 기존 트래픽을 유지해요. 후보와 로그를 확인해요.' >&2
  exit 1
fi
gcloud run services update-traffic "$CLOUD_RUN_SERVICE" --project="$GCP_PROJECT_ID" --region="$GCP_REGION" \
  --to-revisions="$revision=100" --remove-tags="$tag" --quiet
if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  {
    printf 'Commit: `%s`\n\nImage: `%s`\n\nRevision: `%s`\n\nPrevious traffic: `%s`\n' "$IMAGE_SHA" "$pinned" "$revision" "$previous"
  } >> "$GITHUB_STEP_SUMMARY"
fi
