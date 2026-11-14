#!/usr/bin/env bash
# 승인된 프로젝트의 이름·설정만 읽어요. Secret 값은 읽지 않아요.
set -euo pipefail
if [[ $# -ne 1 || ! $1 =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]; then
  echo 'usage: bash scripts/gcp-inventory.sh PROJECT_ID' >&2
  exit 2
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
