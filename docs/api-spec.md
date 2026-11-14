# Wayvi API 명세 (BE 규칙 문서 기준)

- 수정일: 2026-11-12 (이메일 인증·비밀번호 찾기 보강, 담당 표시)
- 기준: `ReelTrip-BE-AGENTS.md`의 API 규칙과 화면별 API 초안. 주소·필드 이름·상태 값·에러 코드는 그 문서를 그대로 따랐습니다.
- 담당: 전체 목록의 "담당" 열. 나눈 기준과 일정은 [work-split.md](work-split.md)에 있습니다.
- Figma 디자인(Figma 디자인, 2026-11-10)을 보고 바꾼 곳은 맨 아래 "Figma 디자인을 보고 바꾼 것"에 모았습니다.
- 상태: 초안입니다. 형식은 금요일 전체 회의에서 FE와 확정합니다.
- ERD: [erd.md](erd.md)
- 2026-11-14: BE1 API 15개의 별도 가짜 서버와 호출 예시는 [api-mock.md](api-mock.md)에 있어요. FE 합의 전 항목을 구분했으며 운영 API 구현 완료를 뜻하지 않아요.

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 주소 | 명사 복수형, 소문자, 하이픈. 접두사(`/api` 등)는 문서에 없어 붙이지 않았습니다 |
| 인증 | `Authorization: Bearer {accessToken}`. `/auth/**`, 읽기 전용 공유 계획 조회, `/actuator/health`만 로그인 없이 열림 |
| 필드 | 카멜 케이스. PK는 `placeId`처럼 도메인명을 붙임. 목록은 `places`, `plans`처럼 `{도메인명}s` |
| Enum | `.name()` 값 그대로: `"category": "FOOD"`, `"status": "DRAFT"` |
| 페이지 | `page`(0부터), `size`, `sortType` |
| 날짜 | `"2026-11-14"`, 날짜·시간은 `"2026-11-14T10:24:00+09:00"` |
| 가격 | `"priceText": "¥960~980"`. 화면에 그대로 보여 주는 문구 하나(50자). BE 규칙 문서의 `{ amount, currency, isFrom }`과 달라 FE 합의가 필요합니다 |
| 성공 응답 | 데이터를 그대로 내려줌. 감싸는 껍데기 없음 |
| 실패 응답 | `{ "code": "DUPLICATE_EMAIL", "message": "이미 가입된 이메일이에요. 로그인해 주세요." }` |

| 상태 코드 | 쓰는 경우 |
| --- | --- |
| `200` | 조회·수정 성공 |
| `201` | 만들기 성공 |
| `202` | 링크 분석을 시작함 |
| `204` | 삭제 성공 |
| `400` | 요청 값이 잘못됨 |
| `401` | 로그인 안 함, 토큰 만료 |
| `403` | 읽기 전용으로 공유받은 계획을 고치려 함 |
| `404` | 대상이 없거나 다른 사람의 카드·계획 |
| `409` | 이미 가입된 이메일 |
| `429` | 인증번호를 너무 자주 요청함 |
| `500` | 서버 오류, 메일 발송 실패 |

| code | message | 화면 |
| --- | --- | --- |
| `DUPLICATE_EMAIL` | 이미 가입된 이메일이에요. 로그인해 주세요. | S0-1b |
| `INVALID_EMAIL_CODE` | 인증번호를 다시 확인해 주세요. | S0-1b |
| `EMAIL_CODE_EXPIRED` | 인증 시간이 지났어요. | S0-1b |
| `EMAIL_CODE_TOO_FREQUENT` | 잠시 후 다시 받아 주세요. (제안) | S0-1b |
| `EMAIL_CODE_ATTEMPTS_EXCEEDED` | 인증번호를 다시 받아 주세요. (제안) | S0-1b |
| `EMAIL_NOT_VERIFIED` | 이메일 인증을 먼저 해 주세요. (제안) | S0-1b |
| `INVALID_PASSWORD_FORMAT` | 영문·숫자 포함 8자 이상으로 입력해 주세요. (제안) | S0-1b, 비밀번호 재설정 |
| `MAIL_SEND_FAILED` | 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요. (제안) | S0-1b |
| `INVALID_RESET_TOKEN` | 링크가 올바르지 않거나 이미 사용됐어요. (제안) | 비밀번호 재설정 |
| `RESET_TOKEN_EXPIRED` | 링크 유효시간이 지났어요. 비밀번호 찾기를 다시 해 주세요. (제안) | 비밀번호 재설정 |
| `INVALID_INPUT` | 입력한 내용을 다시 확인해 주세요. (제안) | 공통 |
| `INVALID_LOGIN` | 이메일 또는 비밀번호를 확인해주세요 | S0-1a |
| `SOURCE_FETCH_FAILED` | 링크가 비공개이거나 지원하지 않는 형식일 수 있어요. | S1-3 |
| `INVALID_PLAN_PERIOD` | 여행 기간을 다시 확인해주세요 | S4-1 |
| `ROUTE_NOT_FOUND` | 경로 정보를 불러오지 못했어요 | S4-7a |
| `PLAN_FORBIDDEN` | 이 계획을 볼 수 있는 권한이 없어요. | S4-8 |
| `PLACE_NOT_FOUND` | (문구 미정) | S3-2 |

## 전체 목록

| 영역 | 메서드 | 주소 | 내용 | 화면 | 담당 |
| --- | --- | --- | --- | --- | --- |
| 인증 | POST | `/auth/email-codes` | 6자리 인증번호 발송 | S0-1b | BE 1 |
| 인증 | POST | `/auth/email-codes/verify` | 인증번호 확인 | S0-1b | BE 1 |
| 인증 | POST | `/auth/signup` | 가입 후 바로 로그인 토큰 발급 | S0-1b | BE 1 |
| 인증 | POST | `/auth/login` | 로그인 | S0-1a | BE 1 |
| 인증 | POST | `/auth/refresh` | 토큰 재발급 | S0-1a | BE 1 |
| 인증 | POST | `/auth/password-reset` | 비밀번호 찾기: 재설정 링크 메일 발송 | S0-1a | BE 1 |
| 인증 | POST | `/auth/password-reset/verify` | 재설정 링크가 쓸 수 있는지 확인 (추가) | 디자인 없음 | BE 1 |
| 인증 | POST | `/auth/password-reset/confirm` | 새 비밀번호 저장 (추가) | 디자인 없음 | BE 1 |
| 회원 | GET | `/users/me` | 닉네임, 보관함 카드 수, 여행목록 수 | S0-2 | BE 1 |
| 링크 | GET | `/sources` | 최근 수집 내역 (Figma 반영) | Figma 링크 홈 | BE 1 |
| 링크 | POST | `/sources` | 여러 링크를 한 번에 분석 시작 | S1-2 | BE 1 |
| 링크 | GET | `/sources/{sourceId}` | 링크별 상태와 카드 초안 | S1-2, S1-3 | BE 1 |
| 카드 | POST | `/places` | 카드 저장(여러 장). 직접 추가도 같은 주소 | S2-1, S3-3 | BE 2 |
| 카드 | GET | `/places` | 보관함 목록, 카테고리별 개수 포함 | S3-1 | BE 2 |
| 카드 | GET | `/places/{placeId}` | 카드 상세 | S3-2 | BE 2 |
| 카드 | PATCH | `/places/{placeId}` | 카드 수정 | S2-2, S3-2 | BE 2 |
| 카드 | DELETE | `/places/{placeId}` | 카드 삭제 | S3-2 | BE 2 |
| 사진 | POST | `/images` | 사진 올리기 → Cloud Storage 주소 | S2-2, S3-3 | BE 1 |
| 장소 검색 | GET | `/place-search` | 이름 → 현지 주소·좌표·사진 | S3-3 | BE 1 |
| 계획 | GET | `/plans` | 작성 중인 계획, 내 계획, 공유받은 계획 | S4-0, S5-1 | BE 2 |
| 계획 | POST | `/plans` | 여행명·기간·장소로 생성, 일자 자동 생성 | S4-1, S4-2 | BE 2 |
| 계획 | GET | `/plans/{planId}` | 일자별 노드·플랜B·숙소 | S4-7 | BE 2 |
| 계획 | PATCH | `/plans/{planId}` | 여행명·기간·장소 수정 (Figma 반영) | Figma 계획 편집 | BE 2 |
| 계획 | DELETE | `/plans/{planId}` | 계획 삭제 | S5-1 | BE 2 |
| 계획 | PUT | `/plans/{planId}/stays` | 고른 숙소 카드 목록 | S4-3 | BE 2 |
| 계획 | PUT | `/plans/{planId}/days/{dayNumber}/areas` | 그 날 둘러볼 구역 이름 목록 | S4-4 | BE 2 |
| 계획 | PUT | `/plans/{planId}/days/{dayNumber}` | 확정한 노드 순서, 플랜B, 완성 여부 | S4-5 | BE 2 |
| 계획 | PATCH | `/plans/{planId}/days/order` | 일자 순서 바꾸기 | S4-7 | BE 2 |
| 계획 | POST | `/plans/{planId}/copies` | 계획 복제 | S5-1 | BE 2 |
| 경로 | GET | `/routes` | 두 장소 사이 가장 빠른 경로 | S4-7a, S4-7b | BE 1 |
| 공유 | POST | `/plans/{planId}/share-links` | 읽기 전용 / 함께 편집 링크 발급 | S4-8 | BE 2 |
| 계획 | POST | `/plans/{planId}/ai-drafts` | AI 초안: 숙소·일자별 노드 순서 채우기 (2026-11-13 추가) | S4-2 | BE 2 |

API는 BE 1이 15개, BE 2가 17개입니다(11/13에 S4-2 AI 초안 추가). 링크 분석의 AI 추출(`infra/ai`)은 API는 아니지만 BE 2 몫입니다.

요청·응답 예시의 값과 문서에 없는 세부 필드는 제가 채운 초안입니다. 문서가 정한 것은 주소, 필드 이름 규칙, 상태 값, 에러 코드입니다.

## 인증·회원

### 이메일 인증 흐름 (S0-1b)

| 단계 | 화면 동작 | API |
| --- | --- | --- |
| 1 | 이메일 입력 → "인증번호 받기" | `POST /auth/email-codes` |
| 2 | 메일로 받은 6자리 입력, 남은 시간(5:00) 표시 | `POST /auth/email-codes/verify` |
| 3 | 비밀번호·닉네임·약관 입력 → "가입하기" | `POST /auth/signup` |

| 규칙 | 값 | 이유 |
| --- | --- | --- |
| 인증번호 | 숫자 6자리, `SecureRandom`으로 생성 | 추측 방지 |
| 유효시간 | 발송 후 5분 | 와이어프레임 S0-1b |
| 다시 받기 | 같은 이메일은 60초 뒤부터. 새로 받으면 이전 번호는 무효 | 메일 폭탄 방지 |
| 틀린 입력 | 한 번호당 5번까지. 5번 틀리면 그 번호는 무효, 다시 받아야 함 | 6자리 대입 공격 방지. `email_code.attempt_count`로 셈 |
| 가입 가능 시간 | 인증번호 발송 후 30분 안에 가입 | 인증만 해 두고 한참 뒤 가입하는 것을 막음 |
| 저장 | 메모리가 아니라 `email_code` 테이블 | Cloud Run 인스턴스가 여러 개여도 같은 결과 (BE 규칙 문서) |
| 가입 후 | 그 이메일의 `email_code` 행을 지움 | |

### POST `/auth/email-codes`

```json
{ "email": "traveler@email.com" }
```

`200`:

```json
{ "expiresAt": "2026-11-14T10:29:00+09:00", "resendAvailableAt": "2026-11-14T10:25:00+09:00" }
```

| 실패 | code |
| --- | --- |
| 이메일 형식이 아님 | `400 INVALID_INPUT` |
| 이미 가입된 이메일 | `409 DUPLICATE_EMAIL` |
| 60초 안에 다시 요청 | `429 EMAIL_CODE_TOO_FREQUENT` |
| 메일 발송 실패 | `500 MAIL_SEND_FAILED` |

### POST `/auth/email-codes/verify`

```json
{ "email": "traveler@email.com", "code": "123456" }
```

`200`:

```json
{ "email": "traveler@email.com", "signupAvailableUntil": "2026-11-14T10:54:00+09:00" }
```

| 실패 | code |
| --- | --- |
| 번호가 틀림 (틀린 횟수 +1) | `400 INVALID_EMAIL_CODE` |
| 5분이 지남 | `400 EMAIL_CODE_EXPIRED` |
| 5번 틀려서 무효가 된 번호 | `400 EMAIL_CODE_ATTEMPTS_EXCEEDED` |
| 그 이메일로 받은 번호가 없음 | `400 INVALID_EMAIL_CODE` |

### POST `/auth/signup`

```json
{
  "email": "traveler@email.com",
  "password": "********",
  "nickname": "보영",
  "isTermsAgreed": true,
  "isPrivacyAgreed": true,
  "isMarketingAgreed": false
}
```

- 인증을 마친 이메일만 가입됩니다. 비밀번호는 영문·숫자 포함 8자 이상. 닉네임은 1~8자.
- 디자인의 회원가입에는 인증번호·약관 화면이 없지만 메일 인증은 유지합니다. 소셜 로그인은 만들지 않습니다.
- `201`:

```json
{ "userId": 1, "nickname": "보영", "accessToken": "...", "refreshToken": "..." }
```

| 실패 | code |
| --- | --- |
| 인증을 안 했거나 30분이 지남 | `400 EMAIL_NOT_VERIFIED` |
| 비밀번호 조건 미달 | `400 INVALID_PASSWORD_FORMAT` |
| 닉네임 길이, 필수 약관 미동의 | `400 INVALID_INPUT` |
| 그 사이 같은 이메일로 가입됨 | `409 DUPLICATE_EMAIL` |

### POST `/auth/login`

```json
{ "email": "traveler@email.com", "password": "********" }
```

`200` → 가입 응답과 같은 구조. 실패는 `401 INVALID_LOGIN`.

### POST `/auth/refresh`

`{ "refreshToken": "..." }` → `200 { "accessToken": "...", "refreshToken": "..." }`

### 비밀번호 찾기 흐름 (S0-1a)

비밀번호는 BCrypt로 한쪽 방향으로만 바꿔 저장하므로 원래 비밀번호를 꺼내 보여 줄 수 없습니다. "비밀번호 찾기"는 와이어프레임 S0-1a대로 가입한 이메일로 재설정 링크를 보내고, 링크에서 새 비밀번호를 정하는 방식입니다.

| 단계 | 화면 동작 | API |
| --- | --- | --- |
| 1 | 로그인 화면 "비밀번호 찾기" → 이메일 입력 → 보내기 | `POST /auth/password-reset` |
| 2 | 메일의 링크 `https://[FE 주소]/password-reset?token={token}`를 열면 링크가 쓸 수 있는지 확인 | `POST /auth/password-reset/verify` |
| 3 | 새 비밀번호·확인 입력 → 저장 → 로그인 화면(S0-1a)으로 | `POST /auth/password-reset/confirm` |

| 규칙 | 값 | 이유 |
| --- | --- | --- |
| 링크 토큰 | 무작위 32바이트를 URL용 문자열로. DB에는 SHA-256 해시(64자)만 저장 | DB가 새도 링크를 만들 수 없게 |
| 유효시간 | 발급 후 30분, 한 번만 사용 | |
| 다시 요청 | 새로 요청하면 이전 링크는 무효. 같은 계정은 60초 안에 다시 요청해도 메일을 보내지 않음(응답은 똑같이 `200`) | 메일 폭탄 방지. 응답을 바꾸면 가입 여부가 드러남 |
| 가입 안 된 이메일 | 메일을 보내지 않고 `200` | 가입 여부를 알려 주지 않음 (BE 규칙 문서) |
| 재설정 후 | 그 회원의 리프레시 토큰을 모두 지움. 자동 로그인은 하지 않음 | 다른 기기의 로그인도 끊음 |
| 화면 | 이메일 입력 화면과 새 비밀번호 화면은 디자인에 없음. FE와 정해야 함 | |

### POST `/auth/password-reset`

```json
{ "email": "traveler@email.com" }
```

`200`. 가입 여부와 관계없이 같은 응답입니다. 이메일 형식이 아니면 `400 INVALID_INPUT`.

### POST `/auth/password-reset/verify`

링크를 열었을 때 FE가 부릅니다. 토큰은 주소가 아니라 본문으로 보내서 서버 로그에 남지 않게 합니다.

```json
{ "token": "q3Z...x8" }
```

`200`:

```json
{ "maskedEmail": "tr*****@email.com", "expiresAt": "2026-11-14T10:54:00+09:00" }
```

| 실패 | code |
| --- | --- |
| 없는 토큰, 이미 쓴 토큰, 새 링크로 무효가 된 토큰 | `400 INVALID_RESET_TOKEN` |
| 30분이 지남 | `400 RESET_TOKEN_EXPIRED` |

### POST `/auth/password-reset/confirm`

```json
{ "token": "q3Z...x8", "newPassword": "********" }
```

`204`. 실패는 `/verify`의 두 가지에 `400 INVALID_PASSWORD_FORMAT`이 더해집니다. 비밀번호 확인 칸이 같은지는 FE에서 검사합니다.

### GET `/users/me`

```json
{ "userId": 1, "email": "traveler@email.com", "nickname": "보영", "placeCount": 27, "planCount": 3 }
```

## 링크 분석

### GET `/sources?page=0&size=10`

링크 홈의 "최근 수집 내역"입니다. 최신순.

```json
{
  "sources": [
    { "sourceId": 12, "title": "오사카 여행 플랜", "status": "DONE", "createdAt": "2026-10-29T10:24:00+09:00" }
  ]
}
```

`title`은 분석이 끝나면 채워집니다. 그 전에는 `null`.

### POST `/sources`

```json
{ "urls": ["https://www.instagram.com/reel/xxxx", "https://youtu.be/yyyy"] }
```

`202 { "sourceId": 12, "status": "PENDING" }`

### GET `/sources/{sourceId}`

FE가 몇 초마다 부릅니다.

```json
{
  "sourceId": 12,
  "title": "오사카 여행 플랜",
  "status": "DONE",
  "sourceLinks": [
    {
      "sourceLinkId": 31,
      "url": "https://www.instagram.com/reel/xxxx",
      "status": "DONE",
      "places": [
        {
          "category": "FOOD",
          "name": "하카타 잇소",
          "address": "福岡市博多区博多駅東3-1-6",
          "latitude": 33.5889,
          "longitude": 130.4245,
          "imageUrl": "https://storage.googleapis.com/...",
          "sourceUrl": "https://www.instagram.com/reel/xxxx",
          "sourceTimestampSec": null,
          "keyPoints": ["돈코츠 라멘"],
          "priceText": "¥960~980",
          "reviewCount": 6500,
          "openingHours": "11:00-24:00",
          "accessText": "하카타역 동쪽 도보 약 6~7분",
          "memo": null
        }
      ]
    },
    {
      "sourceLinkId": 32,
      "url": "https://youtu.be/yyyy",
      "status": "FAILED",
      "code": "SOURCE_FETCH_FAILED",
      "message": "링크가 비공개이거나 지원하지 않는 형식일 수 있어요.",
      "places": []
    }
  ]
}
```

- 상태: `PENDING` → `ANALYZING` → `DONE` 또는 `FAILED`. 전체 상태는 링크가 모두 끝나면 끝납니다.
- 카드 초안은 아직 보관함에 없어서 `placeId`가 없습니다.
- AI가 못 채운 필수 항목은 `null`입니다. 한 링크에서 장소 카드와 교통·팁 카드가 같이 나올 수 있습니다.

## 링크 분석 설계

2026-11-10에 정한 내용입니다. 실제 링크로 시험하기 전이라 "시험 필요"로 적은 곳은 바뀔 수 있습니다.

### 흐름

| 단계 | 하는 일 | 실패하면 |
| --- | --- | --- |
| 1. 접수 | `POST /sources`가 `source`와 `source_link`를 `PENDING`으로 저장하고 바로 `202`를 돌려줌. 링크마다 백그라운드 작업을 하나씩 시작 | 링크가 0개이거나 5개를 넘으면 `400` |
| 2. 내용 가져오기 | 링크 종류를 가려 `platform`에 저장하고 영상이나 본문 텍스트를 가져옴. 상태는 `ANALYZING` | 그 링크만 `FAILED` + `SOURCE_FETCH_FAILED` |
| 3. AI 추출 | LLM이 아래 JSON 형식으로 카드 초안 목록을 냄 | 형식이 다르면 한 번만 다시 시도, 그래도 안 되면 `FAILED` |
| 4. 장소 보강 | 장소 카드는 "장소명 + 도시"로 Google Places를 검색해 주소·좌표·장소 ID·사진·리뷰 수·영업시간을 채움 | 못 찾은 항목은 `null`로 두고 카드는 만듦 |
| 5. 저장 | 초안을 `source_link.draft_json`에 넣고 `DONE`. 모든 링크가 끝나면 `source.title`을 채우고 전체 상태를 끝냄 | |

- 전체 상태(`source.status`)는 링크가 하나라도 `DONE`이면 `DONE`, 모두 `FAILED`면 `FAILED`입니다.
- `source.title`은 초안에서 가장 많이 나온 도시로 "{도시} 여행"을 만듭니다. 도시를 못 얻으면 접수 날짜를 씁니다.

### 링크 종류별 내용 가져오기

| 종류 | 방법 | 상태 |
| --- | --- | --- |
| 유튜브(쇼츠 포함) | Gemini API에 영상 주소를 그대로 넘김. 자막을 따로 뽑지 않고, 장소가 나오는 시점(`sourceTimestampSec`)도 같이 받음 | 시험 필요: 영상 길이·하루 사용량 한도 |
| 인스타 릴스 | 페이지에서 캡션 텍스트만 가져와 LLM에 넘김. 영상 자체는 보지 않음 | 시험 필요: 서버에서 접근이 막히면 `SOURCE_FETCH_FAILED` |
| 블로그·기타 | HTML에서 본문 텍스트를 뽑아 LLM에 넘김 | 네이버 블로그는 본문이 안쪽 프레임에 있어 주소를 바꿔 가져와야 함 |

데모는 유튜브 쇼츠 링크로 준비합니다. 인스타는 되는 만큼만 지원합니다.

### LLM 출력 형식

```json
{
  "city": "후쿠오카",
  "cards": [
    {
      "category": "FOOD",
      "name": "하카타 잇소",
      "keyPoints": ["돈코츠 라멘"],
      "priceText": "¥960~980",
      "accessText": "하카타역 동쪽 도보 약 6~7분",
      "sourceTimestampSec": 42,
      "memo": null
    },
    {
      "category": "TRANSPORT",
      "name": "지하철 1일권",
      "subType": "교통패스",
      "applicableRegion": "후쿠오카",
      "cost": "640엔",
      "benefit": null,
      "method": null,
      "placeText": null,
      "caution": null
    }
  ]
}
```

- `category`는 `Category` enum 키 8개 중 하나만 허용합니다. 다른 값이 오면 그 카드는 버립니다.
- 주소·좌표·사진·리뷰 수·영업시간은 LLM에게 묻지 않습니다. 4단계에서 Google Places 값으로 채웁니다. LLM이 지어낸 주소가 카드에 들어가지 않게 하기 위해서입니다.
- 영상에 없는 값은 `null`로 내게 합니다.

### 정한 것

| 항목 | 결정 | 이유 |
| --- | --- | --- |
| LLM | Gemini API | 유튜브 주소를 영상 입력으로 직접 받음. GCP 안에서 키를 한 곳에서 관리 |
| 백그라운드 실행 | 서버 안의 `@Async` 작업 풀 (`global/config`) | BE 규칙 문서의 "CPU 항상 할당" 전제를 그대로 씀 |
| 멈춘 작업 | `GET /sources/{sourceId}` 때 `ANALYZING`이 3분을 넘은 링크는 `FAILED`로 바꿈 | 서버가 재시작되면 작업이 사라지기 때문 |
| 카드 사진 | Google Places 사진을 Cloud Storage로 복사해 그 주소를 저장. 못 찾으면 유튜브 썸네일 | Places 사진 주소에는 API 키가 들어가 FE에 줄 수 없음 |
| 링크 수 | 요청당 최대 5개 | 디자인의 입력 칸은 4개 |
| 시간 제한 | 링크 하나당 90초 | 넘으면 `FAILED` |
| Places 호출 | 카드 한 장당 검색 한 번, 하루 사용량 한도 설정 | 비용 |

### 내용 가져오기 시험 결과 (2026-11-10)

API 키 없이, 개발용 컴퓨터에서 공개 링크로 2단계만 시험했습니다. Cloud Run에서는 결과가 다를 수 있습니다.

| 종류 | 시험한 것 | 결과 |
| --- | --- | --- |
| 유튜브 쇼츠 | 공식 oEmbed 주소로 제목·채널·썸네일, 페이지에서 설명글 | 모두 가져옴. 다만 설명글에는 해시태그뿐이고 가게 이름이 없어서, 텍스트만으로는 카드를 만들 수 없음. 영상 자체를 읽는 Gemini 입력이 필요하다는 것이 확인됨 |
| 네이버 블로그 | `blog.naver.com/{blogId}/{logNo}` → `PostView.naver?blogId=&logNo=`로 바꿔 본문 | 본문 약 1만 1천 자, 사진 45장을 가져옴. 본문에 붙은 지도 정보에 가게 이름과 주소가 그대로 들어 있어 장소 보강에 쓸 수 있음 |
| 인스타 릴스 | 시험 못 함 | 시험할 릴스 주소가 없었음. 프로필 페이지는 열리지만 HTML에 게시물 정보가 없음(화면에서 따로 불러옴) |

- 유튜브 썸네일은 oEmbed의 `thumbnail_url`로 받을 수 있어, 장소 사진을 못 찾았을 때의 대체 사진으로 쓸 수 있습니다.
- 네이버 블로그는 바깥 페이지(약 3KB)에 본문이 없고 `mainFrame` 안쪽 주소에 있습니다.
- 남은 시험: Gemini에 유튜브 주소 넘기기(키 필요), 인스타 릴스 캡션(릴스 주소 필요), Cloud Run에서 같은 시험.

### 코드 위치

| 하는 일 | 위치 | 담당 |
| --- | --- | --- |
| 접수·조회 | `domain/source/service/SourceService` | BE 1 |
| 2~5단계 진행 | `domain/source/service/AnalysisService` | BE 1 |
| 링크 종류별 내용 가져오기 | `infra/fetcher` | BE 1 |
| LLM 호출과 프롬프트, 응답 → 카드 초안 변환 | `infra/ai/LlmClient` | BE 2 |
| Places 검색·사진 | `infra/map` | BE 1 |
| 사진 복사 | `infra/storage/StorageClient` | BE 1 |

## 요약카드

### POST `/places`

S2-1 "모두 저장"과 S3-3 직접 추가가 같은 주소를 씁니다.

```json
{
  "places": [
    {
      "sourceLinkId": 31,
      "category": "FOOD",
      "name": "하카타 잇소",
      "address": "福岡市博多区博多駅東3-1-6",
      "latitude": 33.5889,
      "longitude": 130.4245,
      "googlePlaceId": "ChIJ...",
      "imageUrl": "https://storage.googleapis.com/...",
      "sourceUrl": "https://www.instagram.com/reel/xxxx",
      "sourceTimestampSec": null,
      "keyPoints": ["돈코츠 라멘"],
      "priceText": "¥960~980",
      "reviewCount": 6500,
      "openingHours": "11:00-24:00",
      "accessText": "하카타역 동쪽 도보 약 6~7분",
      "memo": null
    }
  ]
}
```

- 장소 카드 필수: `imageUrl`, `category`, `name`, `address`, `latitude`, `longitude`. `sourceUrl`은 AI가 만든 카드에서만 필수.
- `keyPoints`는 최대 3개. 서버에서도 검사합니다.
- `priceText`, `memo`는 50자까지(디자인 직접 추가 화면). `reviewCount`, `openingHours`, `accessText`는 없으면 `null`.
- 교통·팁 카드는 아래 항목을 씁니다. 값이 없으면 `null`.

```json
{
  "category": "TRANSPORT",
  "name": "오사카 주유패스",
  "subType": "교통패스",
  "applicableRegion": "오사카",
  "cost": "¥3,300 (1일권)",
  "benefit": "무료 입장 시설, 교통 무제한",
  "method": "구매 후 개찰구에서 사용",
  "placeText": "난바역 관광안내소",
  "caution": "JR 노선은 사용 불가",
  "sourceUrl": "https://..."
}
```

`201 { "places": [ { "placeId": 101, "name": "하카타 잇소", "category": "FOOD" } ] }`

### GET `/places?category=FOOD&page=0&size=20&sortType=LATEST`

```json
{
  "places": [
    {
      "placeId": 101,
      "category": "FOOD",
      "name": "하카타 잇소",
      "address": "福岡市博多区博多駅東3-1-6",
      "latitude": 33.5889,
      "longitude": 130.4245,
      "imageUrl": "https://storage.googleapis.com/...",
      "priceText": "¥960~980",
      "reviewCount": 6500
    }
  ],
  "categoryCounts": { "FOOD": 12, "CAFE": 5, "STAY": 2, "SIGHTSEEING": 4, "ACTIVITY": 1, "SHOPPING": 3, "TRANSPORT": 2, "TIP": 1 },
  "page": 0,
  "size": 20,
  "totalCount": 30
}
```

`category`를 생략하면 전체입니다. `sortType` 값과 페이지 응답 필드 이름은 문서에 없어 정해야 합니다.

구역 주변 카드만 볼 때는 `latitude`, `longitude`, `radiusM`을 붙입니다(Figma 여행지역 선택). 결과가 0개면 FE가 "오사카성 주변에는 저장한 장소가 없어요"를 띄웁니다. `radiusM` 기본값은 미정입니다.

```
GET /places?latitude=34.6873&longitude=135.5262&radiusM=1500
```

### GET `/places/{placeId}`

저장 요청의 카드 한 장과 같은 구조에 `placeId`, `createdAt`, `updatedAt`이 붙습니다. 다른 사람의 카드는 `404`.

### PATCH `/places/{placeId}`

바꿀 필드만 보냅니다. `200` → 수정된 카드.

### DELETE `/places/{placeId}`

`204`.

## 사진·장소 검색

### POST `/images`

`multipart/form-data`, 필드 `file`. `201 { "imageUrl": "https://storage.googleapis.com/..." }`

### GET `/place-search?query=후쿠오카 PARCO`

```json
{
  "places": [
    { "googlePlaceId": "ChIJ...", "name": "福岡PARCO", "address": "福岡市中央区天神2-11-1", "latitude": 33.5914, "longitude": 130.3989, "imageUrl": "https://..." }
  ]
}
```

Google Maps 키는 BE에만 있어서 FE는 이 API를 거쳐 검색합니다.

## 여행계획

### GET `/plans`

| 쿼리 | 값 | 화면 |
| --- | --- | --- |
| `status` | `DRAFT`, `COMPLETED` | S4-0은 `status=DRAFT` |
| `owner` | `me`, `shared` | S5-1 내 계획 / 공유받은 계획 |
| `period` | `UPCOMING`, `PAST` | Figma 여행목록의 "다가오는 여행" / "지난 여행". 종료일이 오늘 이후면 `UPCOMING` |
| `page`, `size`, `sortType` | 페이지 | S5-1 |

```json
{
  "plans": [
    {
      "planId": 7,
      "name": "오사카 3박 4일",
      "startDate": "2026-11-14",
      "endDate": "2026-11-17",
      "city": "오사카",
      "status": "DRAFT",
      "completedDayCount": 1,
      "totalDayCount": 4,
      "placeCount": 5,
      "coverImageUrl": "https://storage.googleapis.com/...",
      "createdAt": "2026-07-17T09:00:00+09:00",
      "ownerUserId": 1,
      "ownerNickname": "보영",
      "members": [
        { "userId": 1, "nickname": "보영", "role": "OWNER" },
        { "userId": 4, "nickname": "민지", "role": "EDITOR" }
      ]
    }
  ],
  "totalCount": 16
}
```

- 진행률은 `completedDayCount / totalDayCount`입니다.
- `coverImageUrl`은 그 계획 첫 노드의 카드 사진입니다. 노드가 없으면 `null`.
- `totalCount`는 "지난 여행 15" 같은 개수 표시에 씁니다.

### POST `/plans`

```json
{
  "name": "오사카 3박 4일",
  "startDate": "2026-11-14",
  "endDate": "2026-11-17",
  "city": "오사카",
  "cityPlaceId": "ChIJ...",
  "latitude": 34.6937,
  "longitude": 135.5023
}
```

- 기간에 맞춰 1일차~n일차를 서버가 만듭니다.
- 여행 장소에 해당하는 보관함 장소 카드를 불러옵니다. 0개여도 계획은 만듭니다.
- `201 { "planId": 7, "status": "DRAFT", "totalDayCount": 4, "loadedPlaceCount": 27 }`
- 기간 오류는 `400 INVALID_PLAN_PERIOD`.

### GET `/plans/{planId}`

```json
{
  "planId": 7,
  "name": "오사카 3박 4일",
  "startDate": "2026-11-14",
  "endDate": "2026-11-17",
  "city": "오사카",
  "status": "DRAFT",
  "role": "OWNER",
  "stays": [
    { "planStayId": 3, "placeId": 55, "name": "오리엔탈 호텔", "latitude": 34.69, "longitude": 135.5 }
  ],
  "days": [
    {
      "planDayId": 21,
      "dayNumber": 1,
      "date": "2026-11-14",
      "isCompleted": true,
      "areas": [
        { "name": "난바", "latitude": 34.6659, "longitude": 135.5013 }
      ],
      "nodes": [
        {
          "planNodeId": 301,
          "orderIndex": 0,
          "placeId": 101,
          "category": "FOOD",
          "name": "이치란 라멘",
          "latitude": 34.668,
          "longitude": 135.501,
          "imageUrl": "https://storage.googleapis.com/...",
          "alternatives": [
            { "planAlternativeId": 9, "placeId": 120, "name": "킨류 라멘" }
          ]
        }
      ]
    }
  ]
}
```

- 응답에 `tips` 목록이 같이 붙습니다. 그 도시의 교통·팁 카드로, 계획 지도의 "팁" 탭에서 보기만 합니다: `"tips": [ { "placeId": 130, "category": "TIP", "name": "지하철 1일권은 640엔", "imageUrl": "..." } ]`
- 읽기 전용으로 공유된 계획은 로그인 없이 조회됩니다. 공유를 켜지 않은 계획은 `404` 또는 `403 PLAN_FORBIDDEN`.
- 함께 편집 링크로 들어온 로그인 사용자는 편집 멤버로 추가됩니다.

### PATCH `/plans/{planId}`

`name`, `startDate`, `endDate`, `city` 중 바꿀 것만. BE 문서에는 없지만 Figma에 "계획 편집" 화면이 있어 넣습니다. 기간을 줄여 없어지는 일자의 노드를 어떻게 할지는 정해야 합니다.

### DELETE `/plans/{planId}`

`204`.

### PUT `/plans/{planId}/stays`

```json
{ "placeIds": [55] }
```

숙소 없이 건너뛰면 빈 목록을 보냅니다. `200` → `stays`.

### PUT `/plans/{planId}/days/{dayNumber}/areas`

```json
{ "areas": [ { "name": "난바", "latitude": 34.6659, "longitude": 135.5013 } ] }
```

좌표는 FE가 `GET /place-search`로 구역을 검색해 얻은 값을 보냅니다.

### PUT `/plans/{planId}/days/{dayNumber}`

그 날의 순서를 통째로 받아 다시 매깁니다.

```json
{
  "nodes": [
    { "placeId": 101, "alternativePlaceIds": [120] },
    { "placeId": 102, "alternativePlaceIds": [] }
  ],
  "isCompleted": true
}
```

- 배열 순서가 `orderIndex`입니다.
- 같은 장소 카드는 한 계획 안에서 한 번만 확정할 수 있습니다.
- 노드가 빠지면 그 노드의 플랜B도 같이 지웁니다.
- 모든 일자가 완성되면 계획 상태가 `COMPLETED`가 됩니다.
- 읽기 전용 멤버가 부르면 `403 PLAN_FORBIDDEN`.

### PATCH `/plans/{planId}/days/order`

```json
{ "dayNumbers": [2, 1, 3, 4] }
```

지금 2일차를 1일차로 옮기는 예시입니다.

### POST `/plans/{planId}/copies`

`201 { "planId": 15 }`. 복제본은 부른 사람의 `DRAFT` 계획이 됩니다.

### POST `/plans/{planId}/ai-drafts` (S4-2 AI 초안, 2026-11-13 추가)

1단계(여행명·장소·기간)로 만든 계획에 AI가 숙소와 일자별 노드 순서를 채웁니다. Notion 기획 "혹은 1. 이후 (→ 계획 초안 생성 → 수정)"을 따른 것이고, 요청 본문은 없습니다.

- 후보: 계획 만든 사람의 보관함에서 계획 도시의 장소 카드(좌표 있는 것, 최근 60장까지). 교통·팁 카드는 넣지 않습니다.
- AI가 좌표를 보고 가까운 곳끼리 같은 날에 묶고, 하루 안은 이동이 짧은 순서로 놓습니다. 숙소는 `STAY` 카드에서 골라 `stays`에 넣고, 하루 최대 8곳, 후보가 많으면 다 넣지 않을 수 있습니다. 플랜B는 만들지 않습니다.
- 기존 노드·플랜B·숙소는 덮어씁니다(FE가 확인 팝업). 모든 일자는 미완성, 계획은 `DRAFT`가 됩니다.
- `200` → `GET /plans/{planId}`와 같은 모양.

| 실패 | code |
| --- | --- |
| 보관함에 계획 도시의 장소 카드가 없음 | `400 AI_DRAFT_NO_PLACES` |
| AI 호출 실패(시간 초과, 형식이 두 번 깨짐) | `500 AI_DRAFT_FAILED` (기존 계획은 그대로) |
| 읽기 전용 멤버 | `403 PLAN_FORBIDDEN` |
| 남의 비공유 계획 | `404 PLAN_NOT_FOUND` |

## 경로

### GET `/routes?from=101&to=102&departAt=2026-11-14T10:00:00+09:00`

`from`, `to`는 `placeId`입니다. `departAt`이 없으면 현재 시각.

```json
{
  "totalMinutes": 18,
  "departAt": "2026-11-14T10:00:00+09:00",
  "arriveAt": "2026-11-14T10:18:00+09:00",
  "segments": [
    { "mode": "WALK", "minutes": 4, "lineName": null, "stopName": null },
    { "mode": "SUBWAY", "minutes": 12, "lineName": "미도스지선", "stopName": "난바역" },
    { "mode": "WALK", "minutes": 2, "lineName": null, "stopName": null }
  ]
}
```

- 대중교통·도보·택시 중 가장 빠른 경로 하나만 내려줍니다. 실패는 `ROUTE_NOT_FOUND`.
- 디자인의 "경로 간단히 보기"는 총 소요시간과 구간 막대만 보여 주고, 자세한 길 안내는 "지도 앱에서 길찾기"로 넘깁니다. 그래서 요금·환승 수·단계별 안내 문구는 뺐습니다. 지도 앱 주소는 FE가 두 장소의 좌표로 만듭니다.

## 공유

### POST `/plans/{planId}/share-links`

```json
{ "shareType": "READ_ONLY" }
```

`201 { "shareUrl": "https://[서비스 주소]/plan/7", "shareType": "READ_ONLY" }`. `shareType`은 `READ_ONLY` 또는 `EDIT`.

## BE 문서에 없어 정해야 하는 것

| 항목 | 내용 |
| --- | --- |
| 로그아웃 | 리프레시 토큰을 서버에서 폐기할지 |
| 계획 수정 | 기간을 줄일 때 없어지는 일자의 노드 처리 |
| 가격 형식 | `priceText` 문구 하나로 가는 것에 대한 FE 합의 |
| 구역 반경 | `radiusM` 기본값 |
| 디자인에 없는 화면 | 인증번호 입력, 약관 동의, 비밀번호 찾기(이메일 입력·새 비밀번호), 마이 탭 |
| 재설정 링크 주소 | 메일에 넣을 FE 페이지 주소(`/password-reset?token=`)와 메일 발송 서비스 |
| 공유 끄기 | 공유 링크를 끄는 API |
| AI 초안 | S4-2. 우선순위 '하'라서 직접 만들기 이후 |
| 페이지 응답 | `sortType` 값과 `totalCount` 등 필드 이름 |

Notion "예외상황 처리"에는 있고 BE 문서에는 없는 동작도 있습니다. 서버가 막을지 FE가 확인 팝업만 띄울지 정해야 합니다.

| 예외 | Notion의 동작 |
| --- | --- |
| 중복 링크 붙여넣기 | "이전에 수집한 적 있는 링크에요, 다시 수집할까요?" |
| 보관함에 이미 있는 장소 | 새 카드로 대체할지 묻기 |
| 계획에 포함된 카드 삭제 | "계획에 포함된 카드에요 정말 삭제하시겠습니까?" |
| 같은 기간에 다른 계획 | "선택한 기간 내에 다른 계획이 존재해요. 계획을 생성하시겠어요?" |

## Figma 디자인을 보고 바꾼 것 (2026-11-10)

| 대상 | 변경 | 디자인 근거 |
| --- | --- | --- |
| `GET /sources` | 추가 | 링크 홈 "최근 수집 내역" |
| 가격 | `price` 객체 → `priceText` 문구 | 카드 "¥960~980", 직접 추가 "1,078엔부터" |
| 카드 | `reviewCount`, `openingHours`, `accessText` 추가 | 카드의 리뷰 수, 영업시간, 찾아가는 길 |
| `GET /places` | `latitude`, `longitude`, `radiusM` 쿼리 추가 | 구역 주변 카드만 표시 |
| 구역 | 이름 목록 → 이름 + 좌표 | 위와 같음 |
| `GET /plans` | `period`, `coverImageUrl`, `members`, `createdAt`, `totalCount` 추가 | 여행목록의 다가오는/지난 여행, 커버 사진, 멤버 아바타 |
| `GET /plans/{planId}` | `tips` 추가 | 계획 지도의 "팁" 탭 |
| `PATCH /plans/{planId}` | 확정 | "계획 편집" 화면 |
| `GET /routes` | 요금·환승 수·단계별 안내 삭제, `stopName` 추가 | "경로 간단히 보기", "지도 앱에서 길찾기" |
| 닉네임 | 1~8자 | 온보딩 안내 문구 |
