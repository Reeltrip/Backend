# 링크 접수·상태 조회 (#16)

S1-2 / S1-3 / S2-1에서 쓰는 POST /sources와 GET /sources/{sourceId}를 구현했어요. JWT 인증을 거치고 로그인한 회원의 수집 내역만 조회해요.

## 이번 범위

- source와 source_link를 한 트랜잭션으로 PENDING 저장하고 202를 반환해요.
- 저장된 상태·제목·링크별 초안 또는 실패 이유를 읽어요.
- 분석 시작·성공·실패를 기록할 엔티티 메서드와 분석 요청 이벤트를 준비했어요.
- 외부 링크 수집, Gemini 호출, Places 보강, 비동기 실행·전체 상태 집계·멈춘 작업 처리는 #18~#23에서 연결해요.

**현재 POST만으로 실제 분석이 시작되지는 않아요.** #23 리스너를 연결하기 전에는 새 요청이 PENDING에 머물러요. 가짜 성공 결과를 운영 API에서 생성하지 않아요. FE의 성공/실패 화면 시뮬레이션은 기존 별도 bootMockRun을 사용해요.

## 접수

```http
POST /sources
Authorization: Bearer {accessToken}
Content-Type: application/json

{"urls":["https://youtu.be/demo?t=42","https://www.instagram.com/reel/demo"]}
```

202 응답은 `{"sourceId":12,"status":"PENDING"}`이에요.

- 링크는 1~5개이고 각 URL은 최대 2048자예요.
- HTTP/HTTPS와 host가 있는 URL만 받고, user:password가 포함된 URL과 잘못된 포트는 거부해요.
- 구문 검사와 기본 플랫폼 분류만 해요. 이 단계에서 DNS 조회·외부 요청은 없어요. 실제 수집 때의 DNS·리디렉션·접근 정책은 #18에서 구현해요.
- 원래 URL과 입력 순서를 유지해요. 중복 링크와 이전에 접수한 URL도 새 요청으로 받아요.
- 한 링크라도 잘못되면 전체를 저장하지 않고 400 INVALID_INPUT이에요. 요청에 없는 회원 ID를 지정할 수 없고, 토큰의 회원이 없으면 401 UNAUTHORIZED예요.
- 카드·보관함 데이터는 만들지 않아요.

## 상태 조회

```http
GET /sources/{sourceId}
Authorization: Bearer {accessToken}
```

sourceId / title / status / sourceLinks를 반환해요. 분석 전 title은 null이에요. sourceLinks는 접수 순서대로 sourceLinkId / url / status / places를 담아요.

- PENDING / ANALYZING: places는 빈 배열이에요.
- DONE: draft_json을 기존 PlaceDraft 목록으로 읽어요. 정보가 부족한 카드의 address·좌표·imageUrl 등은 null을 유지해요.
- FAILED: places는 빈 배열, 링크별 code와 message를 함께 내려요.
- 성공·대기 링크에는 code / message를 넣지 않아요.
- 초안의 sourceLinkId와 sourceUrl은 실제 소유 링크의 ID·원본 URL에 맞춰요. sourceTimestampSec 등 다른 값은 보존하고 placeId는 만들지 않아요.
- 조회로 보관함에 저장하지 않아요. FE가 필요한 값을 채운 뒤 기존 POST /places에 places 목록을 보내 저장해요.
- 다른 회원의 source와 없는 source는 동일한 404 SOURCE_NOT_FOUND예요. 메시지 “수집 내역을 찾을 수 없어요.”는 제안 문구로 FE 확인이 필요해요.
- 저장된 JSON이 손상됐으면 원문을 노출하지 않고 공통 500 INTERNAL_SERVER_ERROR를 반환해요.
- 접수·조회 응답은 no-store로 캐시하지 않아요.

## #23에서 연결할 경계

SourceAnalysisRequested(sourceId)는 접수 트랜잭션 안에서 발행해요. 분석기는 커밋 후 처리하는 [Spring의 TransactionalEventListener](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)를 사용해 저장이 끝난 링크를 읽어야 해요. 이벤트에는 URL이나 카드 본문을 넣지 않아요.

엔티티 메서드:

- Source.markAnalyzing / markDone(title) / markFailed(title)
- SourceLink.markAnalyzing / markDone(draftJson) / markFailed(ErrorCode)

완료·실패한 엔티티를 다시 진행 상태로 바꾸지는 못해요. 실패는 분석 시작 전 대기 상태에서도 기록할 수 있어요. #23은 작업 획득의 동시성, 전체 상태 집계, 시간 제한·복구와 제목 생성을 담당해요. 이 이벤트는 영속 큐가 아니며 이번 구현에서 작업 재시작을 보장하지 않아요.

## 검증

SourceApiTest / SourceUrlParserTest는 202 저장·입력 오류의 전체 미저장, 권한, 모든 상태, 부분 실패·null 필드·출처 보존, 기존 카드 저장 연동, JSON 손상과 플랫폼 경계를 검사해요.

SourceHttpMySqlTest는 선택한 로컬 일회용 MySQL에서 실제 HTTP 접수→AFTER_COMMIT 링크 조회→저장 상태 polling→다른 회원 차단을 확인해요. 테스트가 상태를 준비하며 실제 외부 분석을 수행하지 않아요.

```powershell
# 별도 임시 MySQL에 schema.sql을 적용한 뒤 선택해서 실행해요.
$env:REELTRIP_SOURCE_MYSQL_URL='jdbc:mysql://127.0.0.1:포트/reeltrip_issue16?serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false'
.\gradlew.bat test verifyMockIsolation
```

이 선택 테스트는 localhost의 reeltrip_issue16 / 일회용 root / 빈 비밀번호 DB만 사용해요. 운영 주소·계정은 사용하지 않아요. 환경 변수가 없으면 해당 MySQL 테스트만 건너뛰어요. DB 스키마와 새 의존성 변경은 없어요.
