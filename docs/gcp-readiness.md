# #27 GCP 첫 배포 준비

2026-11-14 작성한 초기 준비안이에요. 2026-11-16 사용자 공유로 프로젝트 wayvi-backend, 서울 Cloud Run(최소1·최대3·CPU 상시)·Cloud SQL MySQL8·공개 읽기 사진 버킷·Secret Manager·WIF 자동 배포가 구성된 상태를 확인했어요. 운영 URL은 https://wayvi-backend-202941360326.asia-northeast3.run.app 이며 이 세션에서도 health UP을 읽어 확인했어요. 기존 배포를 재구성하지 않아요. 아래 사양·권한 표는 원래 준비안이며 실제 IAM·Secret·SQL 사양 전체를 이번 세션에서 조회한 결과는 아니에요.

## 먼저 기존 리소스 확인

승인된 계정으로 로그인한 환경에서 `bash scripts/gcp-inventory.sh PROJECT_ID`를 실행해요. 프로젝트·서비스·DB·버킷·저장소·Secret 이름만 읽으며 Secret 버전을 열지 않아요. 권한 오류·빈 결과는 구분해서 기록하고 기존 리소스를 우선 재사용해요. Cloud Run의 현재 서비스 설정·IAM·WIF 조건도 콘솔에서 확인하되 전체 환경 변수 값은 PR·로그에 붙이지 않아요.

## 검토할 리소스와 권한

| 항목 | 준비안 | 적용 전 확인 |
| --- | --- | --- |
| 리전 | asia-northeast3 | 기존 리소스 리전과 데이터 위치 |
| Cloud Run | Java 21 prod, 1 vCPU / 1 GiB, 인스턴스 기반 과금(CPU 항상 할당), 최소 1 / 최대 3, 동시 요청 20 | 상시 비용, 요청 제한, 실제 메모리, FE 공개 진입 정책 |
| Cloud SQL | MySQL 8, 같은 리전의 작은 승인 사양, 자동 백업·복구 정책 | 실제 DB 사양·저장량·백업 비용·연결 수 |
| DB 연결 | Cloud SQL Auth Proxy를 보조 컨테이너로 두고 앱은 localhost:3306에 JDBC 연결하는 준비안 | proxy의 공식 고정 이미지 digest·시작 probe·순서·CPU/메모리를 첫 서비스 설정에서 검토. 내장 Unix socket만 연결해도 현재 JDBC가 읽는다고 가정하지 않아요. 대안인 private IP / Direct VPC egress는 네트워크 승인 후 별도 검토해요. |
| Artifact Registry | 같은 리전 Docker 저장소, commit SHA 태그 | 보관 기간·이전 revision 이미지 유지 |
| Storage | 사용자 업로드 전용 버킷, 서버만 생성, 공개 읽기는 승인 후 | 조직의 Public Access Prevention·도메인 정책. 공개 읽기 불가면 현재 imageUrl 계약을 바꿔야 하므로 배포를 멈추고 FE와 합의해요. Google Places 사진은 복사하지 않아요. |
| Secret Manager | DB·JWT·Gemini·Maps 고정 버전 | 기존 이름 재사용, 런타임 SA에 필요한 Secret별 accessor만 |
| 런타임 SA | 전용 서비스 계정 | 버킷별 storage.objectCreator, proxy 사용 시 cloudsql.client, Secret별 secretAccessor. Owner/Editor 금지 |
| 배포 SA | 런타임과 분리, WIF만 사용 | AR 저장소별 writer, 서비스별 run.developer, 런타임 SA의 serviceAccountUser, health용 run.invoker. IAM 정책 변경 권한은 별도 관리자가 보유 |
| WIF | GitHub OIDC → 배포 SA | 조직·저장소의 숫자 ID와 ref=refs/heads/main, 승인된 production environment까지 제한. 다른 저장소·브랜치 토큰은 거부 |

월 비용은 현재 가격·크레딧·실제 사양을 확인해 별도로 승인받아요. CPU 상시/최소 1은 유휴 시간에도 비용이 생겨요. 최대 3 × 앱 풀 5 = 최소 15개 연결과 배포 중 겹치는 revision·proxy·운영 연결 여유를 DB 한도에서 계산해요. 분석은 프로세스 내부 실행이므로 요청 후 CPU 상시 할당이 필요하고, 재시작한 작업은 재실행 대신 조회 시 3분 후 실패로 복구돼요.

## 환경 값 연결표

일반 값은 [runtime.env.example](../deploy/runtime.env.example)를 참고해요. 예시 REPLACE 값이 남으면 배포하지 않아요.

| 환경 변수 | 공급·검사 |
| --- | --- |
| DB_URL | Secret 고정 버전. 승인된 proxy라면 jdbc:mysql://127.0.0.1:3306/DB_NAME?serverTimezone=Asia/Seoul&characterEncoding=UTF-8 |
| DB_USERNAME / DB_PASSWORD | Secret 고정 버전. 앱 전용 SQL 사용자, DDL 권한은 수동 적용 담당자와 분리 |
| JWT_SECRET | Secret 고정 버전. 32바이트 이상 무작위, 교체 시 기존 토큰 영향 확인 |
| GEMINI_API_KEY / GEMINI_MODEL | 키는 Secret, 모델은 일반 값. 실제 모델의 지역·할당량·지원 여부 확인 |
| GOOGLE_MAPS_API_KEY | Secret. Places API (New)·Routes API만 허용, API별 일일 한도·예산 알림 설정 |
| GCS_BUCKET | 승인된 업로드 버킷 이름. 런타임은 metadata 토큰으로 쓰고 GCP_ACCESS_TOKEN은 비움 |
| CORS_ALLOWED_ORIGINS / SHARE_BASE_URL | 실제 HTTPS FE 주소. 와일드카드 대신 명시한 origin |
| MAPS_PERSIST_ENRICHMENT | true(기본, 2026-11-15 BE 2 결정). 꺼지면 링크 분석 장소 카드가 저장되지 않아요. Google 저장 정책 문제로 꺼야 하면 false |
| MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM | 가입 인증번호용 Gmail 계정·앱 비밀번호는 Secret, 보내는 주소는 일반 값 (2026-11-15) |

## 적용 순서와 첫 검증

1. 프로젝트·리소스 이름·SA·네트워크·비용·공개 정책을 확정하고 실제 변경 승인을 받아요. Cloud Run/SQL/Admin/AR/Secret/IAM Credentials/STS/Places/Routes 등 필요한 API만 켜요.
2. DB 백업 여부를 확인하고 **빈 대상 DB에만** [schema.sql](schema.sql)을 수동 적용해요. 기존 DB에는 CREATE/DROP를 재실행하지 않고 차이를 먼저 검토해요. 앱은 prod `ddl-auto=validate`를 유지해요.
3. Secret 값은 터미널 출력·셸 기록·PR에 쓰지 않고 안전하게 등록하고 고정 버전을 연결해요. 런타임 SA·업로드 버킷 공개 범위·proxy·인스턴스 과금 설정을 확인해요.
4. 검증한 이미지 digest로 첫 서비스를 배포하고 `GET /actuator/health`의 UP·DB 검증 성공을 확인해요. `/auth/dev-login`이 비활성이고 prod에서 FakeLlmClient가 선택되지 않는지 확인해요.
5. 실제 FE origin의 preflight·로그인·refresh, 업로드 URL 공개 읽기, source 202 이후 상태 진행을 확인해요. 재배포 뒤 사용자·카드·업로드 사진이 유지되는지 확인해요. Maps 출처 표시·실제 경로·Gemini 키도 별도 검증해요.
6. 승인 뒤 #28의 WIF/자동 배포를 켜요. 현재/이전 revision·SHA·secret 버전·health 시각과 성공 범위만 기록해요. 문제 시 이전 revision으로 트래픽을 되돌리고 DB·Secret 호환성을 함께 확인해요.

배포 후 이 문서의 준비안을 실제 확인 결과로 갱신하고 AGENTS.md 배포 표도 함께 갱신해요. 데모 뒤 SQL 정지·리소스 삭제·일정 등록은 별도 승인 후 수행해요.

## 공식 근거 (2026-11-14 확인)

- [Cloud Run 배포](https://cloud.google.com/run/docs/deploying), [과금·CPU 할당](https://cloud.google.com/run/docs/configuring/billing-settings)
- [Cloud SQL 연결](https://cloud.google.com/sql/docs/mysql/connect-run), [WIF 배포 파이프라인](https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [Places 저장·표시 정책](https://developers.google.com/maps/documentation/places/web-service/policies)
