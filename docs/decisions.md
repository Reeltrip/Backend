# 확정된 결정 (AGENTS.md 이후)

- 기준일: 2026-11-12. `AGENTS.md`는 11/4에 쓰여서 아래 내용과 다른 곳이 있습니다. **다르면 이 문서를 따릅니다.**
- 출처: 11/1·11/6 회의록, Figma 디자인(11/10), API 명세서·ERD·역할 분담(11/10~11/12).
- "FE 합의 필요"로 표시한 것은 지금 형태로 만들되, PR 본문 "정한 것"에 적습니다.

## 서비스·일정

- 서비스명은 **Wayvi**(구 ReelTrip). 패키지·레포 이름은 `AGENTS.md`의 `com.reeltrip`을 그대로 써도 됩니다(바꾸려면 사용자에게 먼저 묻기).
- 결과물 제출 11/20(금), 데모데이 11/22(일). 데모는 유튜브 쇼츠 링크로 준비합니다.
- 금요일(11/13) 전체 회의에서 FE와 API 형식을 확정합니다. 그 전까지 아래 형식으로 진행합니다.

## 담당 (AGENTS.md 15줄의 "BE 담당" 확정)

| | 이름 | 맡는 것 |
| --- | --- | --- |
| BE 1 | 반기태 (Notion "기태") | 회원·인증·메일, 링크 접수·가져오기·Places 보강·사진 복사, 사진 올리기, 장소 검색, 경로, 배포, `global/**` |
| BE 2 | 김승윤 | Gemini 추출(`infra/ai`), 요약카드, 여행계획, 공유·멤버 권한 |

- 링크 분석 경계: BE 1이 `FetchedContent`를 넘기고, BE 2가 `LlmClient.extractCards(FetchedContent)`를 구현해 `ExtractionResult(city, List<PlaceDraft>)`를 돌려줍니다(`docs/work-split.md`).
- `GET /users/me`의 카드·계획 개수는 BE 2가 `PlaceService`, `PlanService`에 개수 메서드로 제공합니다. 상대 영역의 레포지토리를 직접 부르지 않습니다.

## API (전체는 `docs/api-spec.md`)

`AGENTS.md`의 화면별 API 초안에서 바뀌거나 늘어난 것:

| 대상 | 결정 |
| --- | --- |
| 가격 | `{ amount, currency, isFrom }` 대신 **`priceText` 문구 하나(50자)**. 디자인의 "¥960~980", "1,078엔부터". FE 합의 필요 |
| 카드 필드 추가 | `reviewCount`, `openingHours`, `accessText` (Google Places 값, 없으면 `null`) |
| `GET /sources` | 추가. 링크 홈 "최근 수집 내역", 최신순. `source.title`은 분석 후 "{도시} 여행" |
| `GET /places` | 구역 주변 카드용 `latitude`, `longitude`, `radiusM` 쿼리 추가(반경 기본값 미정). 응답에 `categoryCounts` |
| 구역 | 이름 목록이 아니라 `{ name, latitude, longitude }` 목록 |
| `GET /plans` | `period`(UPCOMING·PAST), `coverImageUrl`(첫 노드 카드 사진), `members`, `createdAt`, `totalCount` 추가 |
| `GET /plans/{planId}` | `tips`(그 도시의 교통·팁 카드, 조회만) 추가 |
| `PATCH /plans/{planId}` | 추가(Figma "계획 편집"). 기간을 줄일 때 사라지는 일자의 노드 처리는 미정 → 사용자에게 묻기 |
| `GET /routes` | 요금·환승 수·단계별 안내 문구 빼고 `segments`(mode, minutes, lineName, stopName)만 |
| 비밀번호 찾기 | `POST /auth/password-reset` + `/verify` + `/confirm` (BE 1) |
| 이메일 인증 | 5분 유효, 60초 뒤 재발송, 5번 틀리면 무효, 발송 후 30분 안에 가입 (BE 1) |

새 에러 코드: `EMAIL_CODE_TOO_FREQUENT`(429), `EMAIL_CODE_ATTEMPTS_EXCEEDED`, `EMAIL_NOT_VERIFIED`, `INVALID_PASSWORD_FORMAT`, `MAIL_SEND_FAILED`(500), `INVALID_RESET_TOKEN`, `RESET_TOKEN_EXPIRED`, `INVALID_INPUT`, `PLACE_NOT_FOUND`(문구 미정). message 문구는 `docs/api-spec.md` 에러 코드 표 그대로.

## DB (전체는 `docs/erd.md`, `docs/schema.sql`)

- 테이블 15개, 이름은 스네이크 케이스 **단수형**, PK는 `{도메인}_id` (`place_id`, `plan_node_id`).
- BE 2 테이블: `place`, `place_key_point`, `plan`, `plan_day`, `plan_day_area`, `plan_stay`, `plan_node`, `plan_alternative`, `plan_member`.
- `place` 하나에 장소 6종과 교통·팁을 함께 담습니다. 교통·팁 전용 컬럼(`sub_type`, `applicable_region`, `cost`, `benefit`, `method`, `place_text`, `caution`)은 장소 카드에서 `NULL`.
- 카드 초안은 보관함에 넣지 않고 `source_link.draft_json`(BE 1)에 둡니다. "모두 저장" 때 `POST /places`로 `place`가 됩니다.
- 유니크: `plan_day (plan_id, day_number)`, `plan_stay (plan_id, place_id)`, `plan_node (plan_id, place_id)`(같은 장소는 한 계획에 한 번), `plan_member (plan_id, user_id)`.
- 플랜B(`plan_alternative`)는 대신할 노드가 지워지면 같이 지웁니다.
- 공유: `plan.share_type`(`READ_ONLY`·`EDIT`, `NULL`이면 꺼짐). 공유 주소는 `/plan/{planId}`. 공유를 켠 계획만 비회원이 읽기 가능.
- `user`는 H2 등 일부 DB에서 예약어입니다. 테스트 DB를 H2로 쓰면 엔티티에서 테이블 이름 처리가 필요합니다.
- 2026-11-14 사용자 결정: 기존 SQL 파일로 수동 적용하고 prod의 `ddl-auto=validate`를 유지해요. Flyway·새 의존성은 추가하지 않아요. 적용·검증 절차는 [auth-persistence.md](auth-persistence.md)에 있어요.

## 서버에서 검사할 규칙 (BE 2)

- 장소 카드(`Category.isPlace()`)는 `address`, `latitude`, `longitude`, `imageUrl` 필수. 교통·팁은 `subType`, `applicableRegion` 필수.
- `sourceUrl`은 AI가 만든 카드(`sourceLinkId` 있음)만 필수.
- `keyPoints` 최대 3개, `priceText`·`memo` 50자, 닉네임 1~8자.
- 교통·팁 카드는 노드·숙소·플랜B에 못 넣음. `plan_stay`에는 `STAY`만.
- 다른 사람의 카드·계획은 `404`(있다는 것도 알려주지 않음). 읽기 전용으로 공유받은 계획을 고치면 `403 PLAN_FORBIDDEN`.
- 모든 일자가 완성되면 계획 상태 `COMPLETED`. 진행률은 완성 일자 수 / 전체 일자 수.

## 링크 분석·AI (BE 2 부분)

- LLM은 **Gemini API**. 유튜브는 영상 주소를 그대로 넘기고(자막 추출 안 함), 장소가 나오는 시점(`sourceTimestampSec`)도 받습니다. 인스타 릴스·블로그는 BE 1이 가져온 텍스트를 넘깁니다.
- 11/1 사전 검증에서 무료 등급 `gemini-3-flash-preview`로 시험했습니다. 모델 이름과 키는 환경 변수로 받습니다(`GEMINI_API_KEY`, `GEMINI_MODEL`).
- 출력 JSON 형식은 `docs/api-spec.md` "LLM 출력 형식". `category`는 enum 키 8개 외 값이면 그 카드를 버립니다. 형식이 깨지면 한 번만 다시 시도합니다.
- 주소·좌표·사진·리뷰 수·영업시간은 LLM에게 묻지 않습니다(BE 1이 Google Places로 채움). LLM이 지어낸 주소가 카드에 들어가지 않게 하기 위해서입니다.
- 테스트에서 실제 Gemini를 부르지 않습니다. 저장해 둔 응답 JSON으로 파싱·검증을 테스트합니다.

## 기술 스택 (Notion 기술 스택 페이지와 같음)

Java 21, Spring Boot 3.x, Gradle, Spring Data JPA, MySQL 8(Cloud SQL), Spring Security + JWT + BCrypt, JUnit 5 + Mockito, `@Async` 백그라운드 작업, GCP Cloud Run(서울). Swagger(springdoc-openapi)는 2026-11-14 사용자 결정으로 추가했습니다(아래 "Swagger 연결").

## 2026-11-14 Swagger 연결 (BE 2)

- 사용자(BE 2) 결정으로 `springdoc-openapi-starter-webmvc-ui` 2.8.17(Spring Boot 3.5용)을 추가했어요. 로컬·배포 모두 켜고, 끌 때는 환경 변수 `SWAGGER_ENABLED=false`예요.
- 주소: `/swagger-ui/index.html`(화면), `/v3/api-docs`(OpenAPI JSON). "Authorize"에 액세스 토큰을 넣으면 모든 요청에 `Bearer`로 붙어요.
- BE 1 영역을 두 군데 고쳤어요(BE 1 검토 필요): `SecurityConfig`에 Swagger 경로 로그인 없이 열기 한 줄, `global/config/OpenApiConfig` 추가(Bearer 인증 방식, `@LoginUser` 회원 id는 문서 파라미터에서 숨김).
- 2026-11-15 사용자 요청: Swagger 설명을 한국어로 바꿨어요. 묶음 이름(인증·요약카드·여행계획…)·API 요약·설명·쿼리 설명은 컨트롤러에 어노테이션을 붙이지 않고 `OpenApiConfig` 한곳에 모았어요(BE 1 컨트롤러를 건드리지 않으려고). 새 API를 만들면 `OPERATIONS`에 한 줄 추가해요. 묶음은 화면 흐름 순서로 보여 주려고 `tags-sorter: alpha`를 뺐어요. "Try it out"·"Execute" 같은 Swagger 버튼 글자는 라이브러리 화면이라 영어 그대로예요.

## 2026-11-12 BE 2 작업 시작 때 정한 것

문서끼리 어긋나거나 문서에 없던 것을 사용자(BE 2)가 아래로 정했습니다. PR 본문 "정한 것"에도 적습니다.

| # | 대상 | 결정 |
| --- | --- | --- |
| 1 | 숙소 단위 | `AGENTS.md`의 "일자마다 숙소" 대신 스키마대로 계획 단위(`plan_stay`). 일자별 출발·도착 숙소는 저장하지 않음 |
| 2 | 남의 비공유 계획 조회 | `404` (`403 PLAN_FORBIDDEN`은 공유받은 멤버가 권한 밖 동작을 할 때만) |
| 3 | 플랜B 카테고리 | 대신할 노드와 같은 카테고리만 허용 |
| 4 | `memo` 길이 | 서버 검사 50자, 컬럼 `VARCHAR(500)`은 그대로 |
| 5 | `place.city` | 카드 요청·응답에 `city`(선택) 추가. AI 카드는 `ExtractionResult.city`, 직접 추가는 FE가 보냄. 계획 도시와 글자가 같은 카드를 불러옴 |
| 6 | `PATCH /plans/{planId}` 기간 줄이기 | 없어지는 일자와 그 일자의 노드·플랜B·구역을 같이 삭제(FE가 확인 팝업). 시작일이 바뀌면 남은 일자 날짜를 다시 매김 |
| 7 | 공유 멤버 | 공유가 켜진 계획을 로그인 사용자가 `GET /plans/{planId}`로 열면 `READ_ONLY`→`VIEWER`, `EDIT`→`EDITOR`로 추가(이미 멤버면 그대로). 비로그인은 공유가 켜져 있으면 읽기만. 링크 발급·계획 삭제는 `OWNER`만 |
| 8 | 계획에 넣을 수 있는 카드 | 그 계획 `OWNER`·`EDITOR`의 카드 + 이미 그 계획에 있던 카드. 복제는 카드 참조를 그대로 두고 부른 사람의 `DRAFT` 계획 |
| 9 | 계획에 쓰인 카드 삭제 | 그 카드를 쓰는 노드·플랜B·숙소도 같이 삭제. Notion 예외(중복 링크·중복 장소·같은 기간 계획)는 서버가 막지 않음 |
| 10 | BE 2 에러 코드 | `PLACE_NOT_FOUND` 404 "카드를 찾을 수 없어요.", `PLAN_NOT_FOUND` 404 "계획을 찾을 수 없어요.", `PLAN_DAY_NOT_FOUND` 404 "일자를 찾을 수 없어요.", `DUPLICATE_PLAN_PLACE` 409 "이미 다른 일자에 넣은 장소예요.", `INVALID_PLAN_PLACE` 400 "계획에 넣을 수 없는 카드예요.", `AI_EXTRACTION_FAILED` 500 "링크에서 여행 정보를 찾지 못했어요.". 카드 입력 검사는 `INVALID_INPUT` |
| 11 | 작은 값 | `radiusM` 기본 1500, `sortType`은 `LATEST`만, `categoryCounts`는 `category` 필터와 관계없이 세고 반경 필터는 적용, 완성 일자가 다시 미완성이 되면 계획을 `DRAFT`로, 시간대 Asia/Seoul(`+09:00`), Gemini 호출당 40초 제한, 테스트 H2는 `NON_KEYWORDS=USER`로 `user` 테이블 이름 유지 |

## 2026-11-13 S4-2 AI 초안

- `POST /plans/{planId}/ai-drafts`를 추가했습니다(요청 본문 없음, `200` + 계획 보기 응답). 자세한 규칙은 `docs/api-spec.md`. FE 합의 필요.
- 기존 동선을 덮어쓰고, 숙소는 `STAY` 카드 중 AI가 1개를 고르고, 플랜B는 만들지 않습니다.
- 새 에러 코드: `AI_DRAFT_NO_PLACES`(400), `AI_DRAFT_FAILED`(500). 문구는 (제안).
- `GEMINI_API_KEY`가 없으면 링크 분석과 같이 prod는 실패, 그 밖은 가짜 초안(경도 순으로 나누기)을 씁니다.

## 2026-11-14 BE1 개발 방식과 #8 가짜 API

- 사용자 요청으로 #8부터 순서대로 개발하며, 각 이슈에 연결한 `feature/#이슈번호-작업내용` 브랜치를 사용해요. PR도 해당 feature 브랜치에서 main을 대상으로 만들고 다른 BE의 검토를 기다려요.
- #8의 FE 가짜 API는 `src/mock` / `com.reeltripmock`에 두고 `bootMockRun`으로 따로 실행해요. 기존 운영 패키지 `com.reeltrip`은 유지하고 운영 JAR에는 가짜 서버를 포함하지 않아요.
- 기존 `FakeLlmClient`로 장소·교통 카드 예시를 만들어요. 새 라이브러리·DB 테이블·운영 API 변경은 없어요.
- FE 합의 대기 항목과 실행·호출 방법은 [api-mock.md](api-mock.md)에 있어요. 가짜 동작을 실제 인증·메일·링크 분석의 구현 완료로 보지 않아요.

## 2026-11-14 메일 발송 의존성

- 사용자 선택으로 `spring-boot-starter-mail` 추가는 보류해요. #10 실제 메일 전송 방식은 이 결정이 바뀌거나 다른 방식이 정해진 뒤 연결해요.

## 2026-11-14 #12 로그인·재발급 우선 구현

- 사용자 요청으로 메일은 계속 보류하고 #12를 먼저 구현해요. 새 의존성은 없어요.
- 기존 BCrypt·JWT·Clock과 인증 DB를 재사용해요. refresh 원문은 무작위 32바이트, DB 값은 SHA-256 해시예요. 기존 컬럼을 유지해요.
- 재발급은 행 잠금 안에서 이전 해시를 교체하고 로그인 때 정한 14일 만료는 연장하지 않아요. 같은 토큰의 동시 요청은 하나만 성공하고, 교체된 이전 토큰의 재사용은 거부해요.
- 개발용 로그인은 local + 명시적 opt-in에서만 유지하고 prod에서는 비활성화해요. 호출 방식과 FE 확인 사항은 [auth-login.md](auth-login.md)에 있어요.
