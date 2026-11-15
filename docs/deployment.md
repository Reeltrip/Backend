# #28 자동 배포와 롤백

2026-11-14: workflow·스크립트 준비 상태예요. 실제 WIF/IAM/배포 실행은 아직 검증하지 않았어요. 저장소 변수 `DEPLOY_ENABLED`가 정확히 `true`일 때만 main push의 배포 테스트 job이 시작돼요. 기본은 비활성이고 PR은 Java CI만 실행해요.

## 활성화 전에

1. [첫 배포 준비](gcp-readiness.md)를 완료하고 프로젝트·비용·권한·공개 서비스 정책 승인을 받아요. 기존 Cloud Run 서비스에 앱 컨테이너 이름을 `app`으로 두고 Secret 고정 버전·SQL proxy·CPU 상시·최소/최대 인스턴스를 먼저 연결해요. 이 workflow는 IAM·Secret·네트워크·DB 스키마를 만들거나 바꾸지 않아요.
2. GitHub production environment에 허용 브랜치 main과 BE 검토자를 설정해요. WIF는 조직/저장소 숫자 ID, main ref, production environment를 검사하고 해당 배포 SA만 impersonate하도록 제한해요. 장기 JSON 키를 등록하지 않아요. runtime과 deploy SA를 분리하고 필요한 리소스별 권한만 부여해요.
3. GitHub 변수 GCP_PROJECT_ID / AR_REPOSITORY / CLOUD_RUN_SERVICE / WIF_PROVIDER / DEPLOY_SERVICE_ACCOUNT를 채워요. 마지막에 DEPLOY_ENABLED=true를 설정해요. 비밀 값은 GitHub 변수 대신 런타임 Secret Manager에 있어요.
4. 승인된 테스트 배포에서 실제 runner의 테스트 실패 차단, AR 이미지, 후보 URL, promotion, 롤백을 확인해요. 후보 tag URL도 FE가 사용할 공개 진입 정책에서 health를 읽을 수 있어야 해요. IAM 전용 서비스면 현재 검사가 실패해 기존 트래픽을 유지하므로 인증된 probe를 별도로 검토한 뒤 켜요.

## 실행 흐름과 실패

같은 push SHA를 Java 21·H2 테스트와 운영 JAR/mock 격리로 먼저 검증해요. 배포 job에서만 OIDC 권한을 주고 Docker 테스트 빌드 후 SHA 태그로 AR에 올려요. 반환 digest를 고정해 기존 서비스의 app 이미지만 무트래픽 후보 revision `SERVICE-sha-12자리SHA`로 교체해요. SQL proxy 등 다른 컨테이너·런타임 환경은 유지해요.

후보 tag의 `/actuator/health`가 HTTP 성공과 JSON UP을 반환한 뒤 그 revision에 100% 트래픽을 보내요. 실패하면 workflow가 실패하고 이전 트래픽은 유지돼요. 후보 tag는 낮은 비용의 테스트 URL이 아니므로 접근 정책·비용을 동일하게 관리해요. 정리는 별도 승인 후 해요. 같은 SHA 재배포는 기존 revision 충돌로 실패할 수 있으므로 새 수정 커밋을 사용해요. 배포는 직렬로 실행하며 진행 중 배포를 자동 취소하지 않아요.

Actions summary에는 SHA·digest·revision·이전 트래픽만 남겨요. 환경 값·토큰·Secret 본문은 출력하지 않아요. 배포 직후 로그인·refresh·분석·Storage·CORS까지 확인하고 health UP만으로 전체 기능 성공을 판단하지 않아요.

## 수동 롤백

실패가 promotion 이후 발견되면 승인된 관리자가 summary의 **이전 revision**이 READY인지 확인하고 다음 명령을 실제 이름으로 실행해요. 이전 트래픽이 분할돼 있었다면 summary의 각 비율을 복원해요.

```sh
gcloud run services update-traffic SERVICE --project=PROJECT --region=asia-northeast3 --to-revisions=PREVIOUS_REVISION=100
```

서비스 기본 URL에서 health·로그인·DB를 다시 확인하고 원인·SHA·시각을 기록해요. DB 수동 변경과 Secret 값은 revision 롤백으로 복원되지 않아요. JWT 교체·스키마 변경 전에는 이전 revision 호환성과 DB 백업을 먼저 검토해요.

Secret 교체는 새 버전을 만들고 승인 후 기존 서비스의 해당 환경 변수 참조만 새 **고정 버전**으로 바꿔 새 revision을 만들어요. 전체 환경 변수를 덮어쓰거나 latest를 사용하지 않아요. 메일은 계속 보류, MAPS_PERSIST_ENRICHMENT는 true(기본)예요.

공식 근거: [gcloud run deploy](https://cloud.google.com/sdk/gcloud/reference/run/deploy), [트래픽·롤백](https://cloud.google.com/run/docs/rollouts-rollbacks-traffic-migration), [GitHub WIF 인증](https://github.com/google-github-actions/auth). 2026-11-14 확인했어요.
