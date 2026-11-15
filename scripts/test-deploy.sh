#!/usr/bin/env bash
# GCP·Docker·HTTP를 고정 응답으로 바꿔 트래픽 전환 조건을 검사해요.
set -euo pipefail
command -v jq >/dev/null
scratch=$(mktemp -d)
trap 'rm -f -- "$scratch/calls" "$scratch/staged" "$scratch/summary"; rmdir -- "$scratch"' EXIT
export DEPLOY_TEST_DIR="$scratch"
export GCP_PROJECT_ID=reeltrip-test GCP_REGION=asia-northeast3 AR_REPOSITORY=backend
export CLOUD_RUN_SERVICE=backend CLOUD_RUN_CONTAINER=app
export IMAGE_SHA=0123456789abcdef0123456789abcdef01234567
export GITHUB_STEP_SUMMARY="$scratch/summary"
gcloud() {
  printf '%s\n' "$*" >> "$DEPLOY_TEST_DIR/calls"
  case "$*" in
    'run services describe '*)
      if [[ -f $DEPLOY_TEST_DIR/staged ]]; then
        printf '{"status":{"traffic":[{"tag":"verify-0123456789ab","revisionName":"backend-sha-0123456789ab","url":"https://verify-backend-test.a.run.app"}]}}'
      else
        printf '{"spec":{"template":{"spec":{"containers":[{"name":"app"}]}}},"status":{"traffic":[{"revisionName":"backend-old","percent":100}]}}'
      fi ;;
    'artifacts docker images describe '*) printf 'sha256:%064d' 0 ;;
    'run deploy '*) touch "$DEPLOY_TEST_DIR/staged" ;;
  esac
}
docker() {
  printf 'docker %s\n' "$*" >> "$DEPLOY_TEST_DIR/calls"
  [[ ${FAIL_IMAGE:-false} != true ]]
}
curl() {
  [[ ${FAIL_HEALTH:-false} != true ]] || return 22
  printf '{"status":"UP"}'
}
sleep() { :; }
export -f gcloud docker curl sleep
bash scripts/deploy-cloud-run.sh
grep -q -- 'update-traffic.*--to-revisions=backend-sha-0123456789ab=100' "$scratch/calls"
grep -q 'sha256:' "$scratch/summary"
rm -- "$scratch/calls" "$scratch/staged"
if FAIL_HEALTH=true bash scripts/deploy-cloud-run.sh; then
  echo 'health 실패가 성공으로 처리됐어요.' >&2; exit 1
fi
# set -e는 '! 명령'의 실패로 멈추지 않아서 if로 직접 실패시켜요.
if grep -q 'update-traffic' "$scratch/calls"; then
  echo 'health 실패인데 트래픽을 옮겼어요.' >&2; exit 1
fi
rm -- "$scratch/calls" "$scratch/staged"
if FAIL_IMAGE=true bash scripts/deploy-cloud-run.sh; then
  echo '이미지 처리 실패가 성공으로 처리됐어요.' >&2; exit 1
fi
if grep -q 'run deploy' "$scratch/calls"; then
  echo '이미지 처리 실패인데 배포했어요.' >&2; exit 1
fi
echo 'deploy checks: success / health failure / image failure passed'
