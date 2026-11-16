#!/usr/bin/env bash
# 승인된 프로젝트의 이름·설정만 읽어요. Secret 값은 읽지 않아요.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 || ! $1 =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]; then
  echo 'usage: bash scripts/gcp-inventory.sh PROJECT_ID [CLOUD_RUN_SERVICE]' >&2
  exit 2
fi
if [[ $# == 2 ]]; then
  [[ $2 =~ ^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$ ]] || { echo '서비스 이름을 확인해요.' >&2; exit 2; }
  command -v jq >/dev/null || { echo '서비스 점검에는 jq가 필요해요.' >&2; exit 2; }
fi
command -v gcloud >/dev/null || { echo 'gcloud CLI가 필요해요.' >&2; exit 2; }
project=$1
region=asia-northeast3
gcloud projects describe "$project" --format='table(projectId,projectNumber,lifecycleState)'
gcloud services list --enabled --project="$project" --format='value(config.name)'
gcloud run services list --project="$project" --region="$region" --format='table(metadata.name,status.url,status.latestReadyRevisionName)'
gcloud sql instances list --project="$project" --format='table(name,databaseVersion,region,settings.tier,connectionName)'
gcloud storage buckets list --project="$project" --format='table(name,location,iamConfiguration.publicAccessPrevention)'
gcloud artifacts repositories list --project="$project" --location="$region" --format='table(name,format)'
gcloud secrets list --project="$project" --format='value(name)'
gcloud iam service-accounts list --project="$project" --format='table(email,disabled)'
if [[ $# == 2 ]]; then
  # 원본 설정은 화면·파일에 내보내지 않고 허용한 메타데이터만 골라요.
  config=$(gcloud run services describe "$2" --project="$project" --region="$region" --format=json)
  jq '{
    service: .metadata.name,
    url: .status.url,
    latestReadyRevision: .status.latestReadyRevisionName,
    runtimeServiceAccount: .spec.template.spec.serviceAccountName,
    cpuThrottling: (.spec.template.metadata.annotations["run.googleapis.com/cpu-throttling"] // "unknown"),
    minScale: (.metadata.annotations["run.googleapis.com/minScale"] // .spec.template.metadata.annotations["autoscaling.knative.dev/minScale"] // "unknown"),
    maxScale: (.metadata.annotations["run.googleapis.com/maxScale"] // .spec.template.metadata.annotations["autoscaling.knative.dev/maxScale"] // "unknown"),
    concurrency: .spec.template.spec.containerConcurrency,
    containers: [.spec.template.spec.containers[] | {
      name, image,
      envNames: [(.env // [])[] | .name],
      secretReferences: [(.env // [])[] | select(.valueFrom.secretKeyRef != null) |
        {env: .name, secret: .valueFrom.secretKeyRef.name, version: .valueFrom.secretKeyRef.key}],
      plaintextCredentialNames: [(.env // [])[] |
        select(.name | IN("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "GEMINI_API_KEY", "GOOGLE_MAPS_API_KEY", "MAIL_USERNAME", "MAIL_PASSWORD", "GCP_ACCESS_TOKEN")) |
        select(has("value")) | .name]
    }],
    traffic: [.status.traffic[]? | {revisionName, percent, tag}]
  }' <<< "$config"
  unset config
fi
