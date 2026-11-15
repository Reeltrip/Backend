# #29 통합 검증과 남은 연결

2026-11-14 기준이에요. 제출 11/20·데모 11/22는 원문 일정이며 이슈 마감·마일스톤으로 확정하지 않았어요. 실제 FE·외부 서비스·배포 환경까지 연결한 검증은 아직 남아 있어요.

2026-11-16 후속 결정: Places 영구 보강 기본값은 false로 복원해요. 아래 11/14~11/15 보강 검증·제한 기록은 당시 상태이며, 현재 기본값·계약 확인·운영 환경 변수 우선순위는 [places-enrichment.md](places-enrichment.md)를 따라요.

## 자동 재현

Java 21에서 `./gradlew test bootJar verifyMockIsolation`을 실행해요. 기본 테스트는 실제 외부 서비스를 호출하지 않아요. 새 JourneyHttpTest는 임의 루프백 포트의 실제 HTTP·JWT·BCrypt·H2·트랜잭션·분석 실행기를 사용하며 수집/LLM/Maps/Storage만 고정 응답으로 바꿔요.

Docker가 켜진 Windows에서 `pwsh -File scripts/test-mysql.ps1`을 실행하면 루프백에 새 MySQL 8 컨테이너와 빈 DB 5개를 만들고 schema.sql을 수동 적용해요. 선택 테스트를 모두 켜고 전체 suite와 운영 JAR를 검증한 뒤 이 스크립트가 만든 컨테이너만 지워요. Java 21이 필요해요. 한국어·OneDrive 경로에서 Gradle worker/파일 처리 오류가 나면 공백·비 ASCII 문자가 없는 별도 checkout에서 실행해요. 운영 DB 주소를 입력받지 않아요.

| 자동 검증 | 실제 연결 범위 |
| --- | --- |
| JourneyHttpTest / JourneyMySqlTest | 이미 가입된 BCrypt 사용자 로그인, refresh 회전·이전 토큰 거부, source 202→polling, 한 링크 실패·다른 링크 성공, 원본 URL/시점/sourceLinkId 유지, 보강 비활성 상태의 null 유지, 누락 카드 저장 400→사용자 수정 후 저장 201, 보관함 개수, 계획 작성·완성·공유, VIEWER 수정403, 남의 카드·수집·비공유 계획404, 공유 멤버 경로200·비멤버404 |
| 같은 HTTP 시나리오 | multipart PNG 업로드201·저장소 장애500, 일시 장소 검색200·외부 장애502. 실제 GCS/Maps 통신은 아님 |
| AnalysisMySqlTest | MySQL 기본 REPEATABLE READ의 2개 스레드 동시 완료/실패 6회. 모든 링크 종료 시 부모 DONE과 도시 제목 집계, 완료 유실 없음 |
| 기존 선택 MySQL 테스트 | 수동 인증 스키마 validate·제약, HTTP 로그인·refresh 동시 회전, source 접수 커밋과 소유권 |
| 기존 분석 테스트 | 90초 전체 제한, 늦은 결과 무시, 실행 풀 거부, 3분 정체 복구, 롤백 시 외부 호출 없음 |
| scripts/test-deploy.sh | 고정 클라우드 응답으로 health 성공 때만 트래픽 전환, health/이미지 처리 실패 차단. 실제 배포 아님 |

새 테스트는 메일 가입을 구현했다고 가정하지 않고 DB에 테스트 사용자만 준비해요. 기존 BE2의 카드 저장 규칙을 그대로 검증하며 미완성 카드 저장 PR #40의 계약은 이 stack에 포함하지 않아요.

## 로컬 확인 결과

- 2026-11-14 Java 21 / MySQL 8.0 / 수동 schema.sql에서 전체 434개, 실패0·오류0·건너뜀0을 확인했어요. 운영 JAR와 verifyMockIsolation도 통과했어요.
- 새 MySQL 재현 스크립트도 빈 컨테이너부터 실행해 동일한 434개 검증과 자동 정리를 확인했어요. 첫 시도의 PowerShell 호스트 인자 전달 오류는 따옴표로 수정했어요.
- Windows 경로 문제를 피한 ASCII 소스 사본에서 실행했으며 원본의 src·Gradle 파일 253개 SHA-256이 모두 같았어요. 단독 `*` 필터가 Windows에서 파일 이름으로 확장돼 한 번 실패했고, 전체 패키지 필터로 다시 실행해 통과했어요.
- #26 prod Docker는 PORT=8181, health 200 UP, 비인증 /users/me 401, UID10001을 확인했어요. Docker Hub CDN 오류로 공식 Google 공개 이미지 캐시를 빌드 인자로 사용했어요.
- GitHub의 [#26 CI](https://github.com/Reeltrip/Backend/actions/runs/43600099408), [#27 CI](https://github.com/Reeltrip/Backend/actions/runs/43600289288), [#28 CI](https://github.com/Reeltrip/Backend/actions/runs/43600622197)가 실제 runner에서 성공했어요. 앞선 #20~#25 개별 head에는 CI 파일이 없어 checks가 없고, 후속 stack의 전체 테스트에 포함돼요.
- actionlint·bash 문법·배포 전환 모의 검사도 통과했어요. 로컬 GCP 리소스 조회 스크립트는 잘못된 인자를 거부하며, 실제 조회는 gcloud·인증 부재로 실행하지 않았어요.

## 배포 환경에서 추가 확인할 요청

승인된 테스트 계정으로만 실행하고 토큰·이메일·Secret을 증거 문서에 남기지 않아요. 응답 코드·비밀이 없는 요약·시각·revision·SHA만 기록해요.

1. health 200 UP, 실제 FE origin의 CORS preflight, prod 개발 로그인 차단, Gemini 키 누락 시 가짜 성공이 없는지 확인해요.
2. 실제 로그인→refresh→이전 refresh401. 메일 보류 해제 뒤에는 인증번호→가입→비밀번호 재설정→기존 refresh 폐기까지 추가해요.
3. 본인이 공개한 YouTube Shorts와 접근 실패 링크를 함께 POST /sources 해요. 202 이후 GET /sources/{id}에서 링크별 결과를 기다리고, 누락 필드·원본 URL·시점·sourceLinkId를 확인해요. 검증한 공개 데모 링크는 아직 선정하지 않았어요.
4. 사용자 사진을 POST /images로 올려 공개 URL을 확인하고, 직접 확인한 주소·좌표로 초안을 수정한 뒤 POST /places→GET /places→POST /plans→PUT days를 확인해요. 영구 Places 보강과 Google 사진 복사는 켜지 않아요.
5. 실제 Maps 장소 검색·출처 표시·대중교통 경로를 FE와 확인해요. 교통수단·택시 추정·오류 문구·출처 필드는 아직 FE 합의가 필요해요. Google 경로 응답은 캐시하지 않아요.
6. 읽기 전용 링크를 다른 계정으로 열어 읽기200/수정403, 다른 사람의 수집·카드404를 확인해요. 재배포 뒤 사용자·카드·사진 유지와 분석 정체 복구를 확인해요.
7. [배포 절차](deployment.md)에 따라 후보 health 실패 시 이전 트래픽 유지와 이전 revision 롤백을 실제 승인 환경에서 검증해요.

## 남은 제한

- 2026-11-16: #10·#11·#13은 PR #64로 main에 반영됐고 사용자가 운영 실메일 가입 성공을 공유했어요. #14는 별도 feature 브랜치에서 구현·검증하며 운영 적용은 리뷰·병합 후예요. #17 PR #36은 최신 main 충돌을 해소해 별도 검증 중이에요.
- #22는 2026-11-15 BE 2 결정으로 보강 기본 true(키가 없으면 빈 값 유지), Places 사진 Storage 복사 없음이에요. 저장 정책·계약 예외·FE 출처 표시 확인이 남아 있어요.
- 공개 정적 웹·네이버 본문·Instagram 공개 캡션 수집은 코드와 테스트가 있어요. 로그인/동적 렌더링/영상 다운로드는 지원하지 않으며 실제 Cloud Run의 플랫폼 차단 여부는 미검증이에요.
- 2026-11-16 사용자 공유: GCP 배포와 PR #64의 WIF 자동 배포, 운영 로그인·쇼츠 분석·카드·AI 초안·경로·사진 업로드 실호출이 완료됐어요. 이번 세션이 직접 확인한 운영 범위는 health·Swagger예요. 실제 롤백·FE 재설정 화면·재설정 실메일은 아직 확인하지 않았어요.
- #20~#28 PR은 #43→#44→#45→#46→#47→#48→#49→#50→#51 순으로 선행 브랜치 위에 쌓았어요. #29도 #51 위에 올려요. 선행 PR을 병합한 뒤 다음 PR base를 main으로 바꾸고 다른 BE 검토를 받아요.
