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
- 스키마 생성 방식(`ddl-auto`·마이그레이션 도구)은 정해지지 않았습니다 → 시작할 때 사용자에게 묻기.

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

Java 21, Spring Boot 3.x, Gradle, Spring Data JPA, MySQL 8(Cloud SQL), Spring Security + JWT + BCrypt, JUnit 5 + Mockito, `@Async` 백그라운드 작업, GCP Cloud Run(서울). Swagger(springdoc-openapi)는 제안 상태라 의존성 추가 전에 묻습니다.
