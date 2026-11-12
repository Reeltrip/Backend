# Wayvi BE 역할 분담 (BE 1 / BE 2)

- 작성일: 2026-11-12 (제안)
- 이름은 팀에서 정해 주세요. 정해지면 `ReelTrip-BE-AGENTS.md`의 "BE 담당: BE 1 (이름) / BE 2 (이름)" 줄과 Notion API 명세의 "구현자" 칸에 같이 적으면 됩니다.
- API별 담당은 [api-spec.md](api-spec.md) 전체 목록의 "담당" 열에도 있습니다.

## 나눈 기준

11/1 회의에서 정한 개발 역할을 바탕으로 했습니다.

| 회의 때 정한 것 | 이 안 |
| --- | --- |
| BE 1: 링크 → 텍스트 추출, 서버 배포 | 그대로 |
| BE 2: 텍스트 → 요약카드, 좌표 변환, DB·API, 계획 저장 | 그대로이되 **좌표 변환(Google Places 보강)은 BE 1로** |
| (그때 없던 것) 회원가입·로그인·메일 인증·비밀번호 찾기 | **BE 1** |
| (그때 없던 것) 경로, 장소 검색, 사진 올리기 | **BE 1** |
| (그때 없던 것) 계획 공유·멤버 권한 | **BE 2** |

바꾼 이유는 두 가지입니다.

- 그 뒤 인증·공유·경로가 늘면서 회의 때 나눈 대로면 BE 2가 거의 모든 API를 맡게 됩니다. 계획 편집만으로도 API가 10개라, 인증과 외부 API를 BE 1로 옮겼습니다.
- Google Maps를 부르는 곳(장소 보강, 장소 검색, 경로)을 한 사람이 `infra/map`에서 같이 다루면 API 키, 하루 사용량 한도, 사진 복사를 한 곳에서 관리할 수 있습니다.

## 한눈에

| | BE 1 — 회원·링크 분석·외부 API·배포 | BE 2 — AI 추출·요약카드·여행계획·공유 |
| --- | --- | --- |
| 화면 | S0 시작·로그인·가입, S1 링크, S3-3 장소 검색, S4-7a·b 경로 | S2 요약카드, S3 보관함, S4 여행계획, S5 여행목록, S4-8 공유 |
| API | 15개 | 16개 |
| 테이블 | `user`, `email_code`, `password_reset_token`, `refresh_token`, `source`, `source_link` | `place`, `place_key_point`, `plan`, `plan_day`, `plan_day_area`, `plan_stay`, `plan_node`, `plan_alternative`, `plan_member` |
| 코드 | `global/security`, `global/config`, `domain/auth`, `domain/user`, `domain/source`, `domain/route`, `domain/image`, `domain/place/controller/PlaceSearchController`, `infra/fetcher`, `infra/map`, `infra/mail`, `infra/storage` | `domain/place`(검색 컨트롤러 제외), `domain/plan`, `infra/ai` |
| 배포 | `Dockerfile`, GitHub Actions, Cloud Run, Cloud SQL, Cloud Storage, Secret Manager | 없음 |
| 꼭 쓸 테스트 (BE 규칙 문서) | 인증번호 만료·틀림·5회 초과, 중복 가입, 재설정 링크 만료·재사용 | 노드 확정·순서·플랜B, 일자 순서, AI 응답 → 카드 초안 변환, 다른 사람 카드·계획 `404`, 읽기 전용 수정 `403` |

### API 목록

| BE 1 (15) | BE 2 (16) |
| --- | --- |
| `POST /auth/email-codes` | `POST /places` |
| `POST /auth/email-codes/verify` | `GET /places` |
| `POST /auth/signup` | `GET /places/{placeId}` |
| `POST /auth/login` | `PATCH /places/{placeId}` |
| `POST /auth/refresh` | `DELETE /places/{placeId}` |
| `POST /auth/password-reset` | `GET /plans` |
| `POST /auth/password-reset/verify` | `POST /plans` |
| `POST /auth/password-reset/confirm` | `GET /plans/{planId}` |
| `GET /users/me` | `PATCH /plans/{planId}` |
| `GET /sources` | `DELETE /plans/{planId}` |
| `POST /sources` | `PUT /plans/{planId}/stays` |
| `GET /sources/{sourceId}` | `PUT /plans/{planId}/days/{dayNumber}/areas` |
| `POST /images` | `PUT /plans/{planId}/days/{dayNumber}` |
| `GET /place-search` | `PATCH /plans/{planId}/days/order` |
| `GET /routes` | `POST /plans/{planId}/copies` |
| | `POST /plans/{planId}/share-links` |

API 수는 비슷하지만 일의 성격이 다릅니다. BE 1은 외부 서비스(메일, 링크 가져오기, Google Maps, GCP)와 맞물려 막히기 쉬운 일이 많고, BE 2는 계획 편집처럼 규칙이 많아 테스트할 것이 많은 일입니다. 대략 잡은 작업량은 둘 다 8일 안팎입니다.

## 링크 분석은 둘이 나눠서

링크 분석 한 흐름에 두 사람이 걸려 있어서 경계를 먼저 정해야 합니다.

| 단계 | 담당 | 넘겨주는 것 |
| --- | --- | --- |
| 1. 접수, 백그라운드 작업 시작, 상태 관리 | BE 1 | |
| 2. 링크 종류 판별, 본문·캡션 가져오기 | BE 1 | `FetchedContent` (아래) |
| 3. Gemini 호출, 응답 JSON → 카드 초안 | **BE 2** | `ExtractionResult` (아래) |
| 4. 장소명 + 도시로 Google Places 보강, 사진 Cloud Storage 복사 | BE 1 | |
| 5. `draft_json` 저장, `GET /sources/{sourceId}` 응답 | BE 1 | |
| 6. "모두 저장" → `POST /places` | BE 2 | |

첫날 두 사람이 아래 두 타입만 먼저 합의해서 `main`에 넣으면 각자 따로 진행할 수 있습니다. BE 1은 BE 2의 구현이 들어오기 전까지 가짜 `LlmClient`(정해진 카드 2장을 돌려줌)로 흐름을 만듭니다.

```java
// BE 1이 만들어서 넘김 (infra/fetcher)
public record FetchedContent(
        Platform platform,   // YOUTUBE, INSTAGRAM, NAVER_BLOG, ETC
        String url,
        String text,         // 본문·캡션. 유튜브는 null (Gemini가 영상을 직접 읽음)
        String thumbnailUrl  // 장소 사진을 못 찾았을 때 대신 쓸 사진
) {}

// BE 2가 구현 (infra/ai)
public interface LlmClient {
    ExtractionResult extractCards(FetchedContent content);
}

public record ExtractionResult(String city, List<PlaceDraft> cards) {}
```

`PlaceDraft`는 `POST /places` 요청의 카드 한 장과 같은 필드로 BE 2가 정합니다. 주소·좌표·사진·리뷰 수·영업시간은 비워서 넘기고, BE 1이 4단계에서 채웁니다.

## 서로 기대는 곳

| 무엇 | 만드는 사람 | 쓰는 사람 | 언제까지 |
| --- | --- | --- | --- |
| 프로젝트 뼈대, `ErrorCode`·`BusinessException`·`GlobalExceptionHandler`, `BaseTimeEntity` | BE 1 | 둘 다 | 11/13 |
| `User` 엔티티 | BE 1 | BE 2 (계획 소유자, 멤버 닉네임) | 11/13 |
| JWT 필터와 로그인 사용자 꺼내기(예: `@LoginUser Long userId`) | BE 1 | BE 2의 모든 API | 11/14 |
| `Category` enum (`isPlace()` 포함) | BE 2 | BE 1 (초안 검사) | 11/13 |
| `FetchedContent`, `LlmClient`, `ExtractionResult`, `PlaceDraft` | 위 표대로 | 둘 다 | 11/13 |
| `SourceLink` 엔티티 | BE 1 | BE 2 (`place.source_link_id`) | 11/14 |
| 카드·계획 개수 (`GET /users/me`용) | BE 2 (`PlaceService`, `PlanService`에 개수 메서드) | BE 1 | 11/17. 그 전에는 0 |

- `ErrorCode`는 두 사람이 같이 고치는 파일입니다. 각자 자기 블록(`// BE 1: 인증·링크`, `// BE 2: 카드·계획`) 안에만 추가하면 합칠 때 충돌이 거의 없습니다.
- 상대 영역의 레포지토리를 직접 부르지 않고 서비스 메서드를 통해 부릅니다.
- PR은 서로의 것을 확인하고 합칩니다 (BE 규칙 문서).

## 일정 (제안)

제출 11/20(금), 데모데이 11/22(일) 기준입니다. FE가 기다리지 않게 둘 다 **가짜 응답 API를 먼저** 엽니다 (BE 규칙 문서: `// TODO: 가짜 응답, 실제 구현으로 교체`).

| 날짜 | BE 1 | BE 2 |
| --- | --- | --- |
| 11/13(금) | 전체 회의에서 API 확정. 프로젝트 뼈대·공통 예외·`User` 엔티티를 먼저 `main`에 | `Category` enum, 카드·계획 엔티티. 위 인터페이스 합의 |
| 11/14(토) | JWT, 인증 API 가짜 응답, `source` 엔티티 | 카드·계획 API 가짜 응답 → FE 연동 시작 |
| 11/15(일) | Cloud Run 첫 배포와 자동 배포, `/sources` 가짜 응답 | Gemini에 유튜브 쇼츠 주소 넣어 보기 (키 필요), 프롬프트 |
| 11/16(월) | 인증 실제 구현 (메일 인증·가입·로그인·재설정) | 카드 실제 구현, 계획 생성·조회 |
| 11/17(화) | 링크 가져오기(유튜브·블로그·릴스), 백그라운드 분석 흐름 | 일자 저장(노드 순서·플랜B), 숙소·구역 |
| 11/18(수) | Places 보강·사진 복사, 사진 올리기, 장소 검색, 경로 | 일자 순서, 복제, 공유·권한, 팁 목록 |
| 11/19(목) | 같이: 링크 → 카드 → 계획 전체 흐름 확인, 데모용 링크 미리 분석, 배포 서버에서 확인 | |
| 11/20(금) | 제출 | |

## 정해야 할 것

- 두 사람 이름과 누가 BE 1, BE 2를 맡을지
- Gemini API 키와 메일 발송 서비스(Gmail SMTP 등) 계정을 누가 만들지. 이 안에서는 BE 1이 Secret Manager에 넣고, Gemini 키는 BE 2도 로컬에서 써야 하므로 같이 받습니다.
- 회의 때 정한 대로 좌표 변환을 BE 2에 두고 싶다면, 장소 검색·경로까지 BE 2로 옮겨 `infra/map`을 통째로 넘기는 편이 낫습니다. 그 대신 인증을 BE 2로 넘기면 계획 일정이 밀릴 수 있습니다.
