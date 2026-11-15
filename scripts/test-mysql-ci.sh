#!/usr/bin/env bash
# CI의 새 루프백 서비스만 사용해요. 운영 DB 주소는 받지 않아요.
set -euo pipefail
[[ ${GITHUB_ACTIONS:-} == true ]] || { echo 'GitHub Actions의 일회용 MySQL 전용이에요.' >&2; exit 2; }
names=(reeltrip_issue9 reeltrip_issue12 reeltrip_issue14 reeltrip_issue16 reeltrip_issue17 reeltrip_journey reeltrip_analysis)
for database in "${names[@]}"; do
  mysql --protocol=TCP --host=127.0.0.1 --port=33306 --user=root -e "CREATE DATABASE $database CHARACTER SET utf8mb4"
  mysql --protocol=TCP --host=127.0.0.1 --port=33306 --user=root "$database" < docs/schema.sql
done
base='jdbc:mysql://127.0.0.1:33306/'
options='?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export REELTRIP_MYSQL_URL="${base}reeltrip_issue9$options"
export REELTRIP_AUTH_MYSQL_URL="${base}reeltrip_issue12$options"
export REELTRIP_RESET_MYSQL_URL="${base}reeltrip_issue14$options"
export REELTRIP_SOURCE_MYSQL_URL="${base}reeltrip_issue16$options"
export REELTRIP_HISTORY_MYSQL_URL="${base}reeltrip_issue17$options"
export REELTRIP_JOURNEY_MYSQL_URL="${base}reeltrip_journey$options"
export REELTRIP_ANALYSIS_MYSQL_URL="${base}reeltrip_analysis$options"
bash ./gradlew --no-daemon test bootJar verifyMockIsolation
