이 파일은 ReelTrip 백엔드 레포의 작업 규칙이에요. 사람도, AI 코딩 도구(Codex, Claude Code 등)도 같은 규칙으로 일해요.

- 레포 루트에 `AGENTS.md`라는 이름으로 두면 Codex가 읽어요.
- Claude Code를 쓰는 사람은 루트에 `CLAUDE.md`를 만들고 `@AGENTS.md` 한 줄만 적으면 같은 내용을 읽어요.

## **프로젝트 요약**

- **서비스:** 여행 릴스·유튜브·블로그 링크를 넣으면 AI가 요약카드로 정리하고, 카테고리 보관함(지도 노드)과 Day별 동선으로 여행 계획을 짜고 공유하는 웹앱
- **BE가 하는 일:** 회원가입·로그인, 링크 내용 가져오기와 AI 요약, 요약카드·여행계획 저장, 구간 경로 조회, 계획 공유를 API로 제공해요.
- **스택:** Java 21 + Spring Boot 3, Gradle, Spring Data JPA, Spring Security(JWT), MySQL, AI 요약은 외부 LLM API
- **외부 API:** Google Maps (장소 검색·좌표, 대중교통 경로), 메일 발송 (인증번호·비밀번호 재설정)
- **배포:** GCP. 서버는 Cloud Run, DB는 Cloud SQL (MySQL), 사진은 Cloud Storage, 비밀 값은 Secret Manager, 자동 배포는 GitHub Actions
- **일정:** 결과물 제출 11/20 (금), 데모데이 11/22 (일). 예선은 FE 배포 URL로 심사하고 그 화면이 BE 서버를 불러요. 그래서 BE `main`과 배포 서버도 항상 동작해야 해요.
- **화면 기준:** 와이어프레임 화면 번호(S0-1 ~ S5-1)를 이슈·PR·코드 주석에서 그대로 써요. 예: `// S1-3 정보 추출 실패 화면에서 쓰는 코드`
- **BE 담당:** BE 1 (이름) 담당 영역 / BE 2 (이름) 담당 영역 <!-- 팀에서 채워 주세요 -->

## **AI 도구 응답 방식**

팀원은 바쁘고 작업 과정을 다 지켜보지 않아요. 잠깐 자리를 비웠다 돌아온 동료에게 말하듯 답해 주세요.

- 한국어로, "~요" 체로 답해요.
- 첫 문장은 결과예요. 요청한 일이 됐는지, 무엇을 찾았는지를 먼저 말하고 설명은 그 뒤에 붙여요.
- 짧게 써요. 뜻을 더하지 않는 말("진짜", "솔직히", "중요한 점은")과 "이제 ~를 설명할게요" 같은 예고는 빼요.
- 쉬운 말로 써요. 전문용어는 꼭 필요할 때만 쓰고, 작업하면서 스스로 만든 이름이나 줄임말은 쓰지 않아요.
- 작업을 시작하기 전에 무엇을 할지 한 문장으로 말해요.
- 여러 선택지를 비교할 때는 표로 정리하고, 추천을 하나 골라 이유와 함께 말해요. 하지 않을 선택지를 길게 나열하지 않아요.
- 파일을 언급할 때는 `PlaceService.java`처럼 클릭할 수 있는 링크로 써요.
- 실패한 것, 건너뛴 것, 확인하지 못한 것은 숨기지 않고 그대로 말해요. 테스트가 실패했으면 실패 내용을 그대로 보여 줘요.
- 질문은 정말 막혔을 때만 해요. 이 파일이나 기존 코드로 정할 수 있는 건 정해서 진행하고, 무엇으로 정했는지 알려 줘요.
- 코드 주석도 같은 원칙이에요. 짧은 한국어로, 코드만 봐서는 알기 어려운 이유만 적어요.

### **답변 끝의 진행 상황 요약**

파일을 고치거나 명령을 실행한 답변은 끝에 진행 상황을 짧게 정리해요. 질문에 바로 답하는 짧은 대화에는 붙이지 않아요.

- **지금 상태:** 무엇이 바뀌었고 어디까지 됐는지 2~3문장
- **남은 것:** 안 된 것, 확인 못 한 것 (커밋 안 함, 배포 서버에서 확인 안 함 등)
- **다음 할 일:** 이어서 해야 할 작업. 사용자가 정해야 할 게 있으면 질문 하나로 묻고 추천안을 먼저 적어요.

```
지금 상태: GET /places에 카테고리 필터를 붙였고 로컬에서 6개 장소 카테고리와 교통·팁 모두 응답을 확인했어요.
남은 것: 테스트 코드는 아직이고, 커밋하지 않았어요.
다음 할 일: 커밋할까요? (추천: feat: 보관함 카테고리 필터 API 추가 (#21))
```

## **AI 도구 작업 규칙**

- 커밋, 푸시, PR 생성은 요청받았을 때만 해요. 할 때는 아래 커밋·브랜치 규칙을 따라요.
- `build.gradle`에 새 의존성을 추가하기 전에 무엇을 왜 추가하는지 먼저 물어봐요.
- API 키, DB 비밀번호, JWT 비밀 키는 코드에 직접 쓰지 않아요. 환경 변수로 받고, 로컬 값은 `.env`나 `application-local.yml`에만 둬요 (둘 다 `.gitignore`에 넣어요). 배포 값은 Secret Manager에 둬요. 이 값들은 출력하지 않아요.
- Google Maps API 키는 BE에만 둬요. FE가 경로·장소 검색이 필요하면 BE API를 거쳐서 불러요.
- GCP 설정(Cloud Run 서비스, Cloud SQL, Cloud Storage, Secret Manager, 권한)을 바꾸거나 `gcloud`로 배포하는 일은 요청받았을 때만 해요. 콘솔에서 바꾼 설정은 아래 배포 표에도 고쳐 적어요.
- API 형식은 금요일 전체 회의에서 FE와 정한 것을 따라요. 바꿔야 하면 코드보다 먼저 FE에 알리고 노션 API 명세를 고쳐요.
- 아직 안 만든 API를 FE가 먼저 써야 하면, 정한 형식대로 가짜 응답을 내려주는 API를 먼저 만들어요. 가짜라는 걸 코드에 표시해요 (`// TODO: 가짜 응답, 실제 구현으로 교체`).
- 와이어프레임에 "확인 필요"로 표시된 동작은 마음대로 정하지 않아요. 가장 단순한 쪽으로 만들고, PR 본문에 무엇으로 정했는지 적어요.
- DB 테이블·컬럼을 바꾸면 PR 본문에 무엇이 바뀌었는지 적어요. 배포 DB의 데이터를 지우는 변경은 하기 전에 물어봐요.
- 외부 호출(링크 내용 가져오기, LLM, Google Maps, 메일)은 느리거나 실패할 수 있어요. 시간 제한을 두고, 실패하면 FE가 실패 화면을 보여줄 수 있는 에러 코드를 내려줘요 (본선 안정성·예외 처리 점수).
- API를 만들면 로컬에서 직접 호출해 성공·실패 응답을 모두 확인해요.

## **커밋 메시지**

```
<type>: <무엇을 왜 바꿨는지> (#이슈번호)
```

```
feat: 여러 링크를 한 번에 분석하는 API 추가 (#12)
fix: 플랜B를 지운 뒤 노드 순번이 다시 매겨지지 않는 문제 수정 (#18)
```

| **type** | **쓰는 경우** |
| --- | --- |
| `feat` | 새 기능, 새 API |
| `fix` | 버그 수정 |
| `refactor` | 동작은 그대로 두고 코드 구조 개선 |
| `style` | 포매팅, 줄바꿈 등 코드 의미가 바뀌지 않는 수정 |
| `remove` | 파일·코드·기능 삭제 |
| `chore` | 의존성 추가, 빌드·환경 설정, 배포 설정, `.gitignore` 등 |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 |
- 어떻게(how)보다 무엇(what)과 왜(why)를 적어요.
- 하나의 커밋에는 하나의 일만 담아요.

## **브랜치**

| **브랜치** | **설명** |
| --- | --- |
| `main` | 배포 브랜치. 합쳐지면 GitHub Actions가 Cloud Run에 자동 배포해요. 직접 푸시하지 않고 PR로만 합쳐요. |
| `feature/#이슈번호-작업내용` | 작업 브랜치. 예: `feature/#12-source-api` |
- 작업 브랜치는 이슈 하나당 하나씩 만들어요.
- PR을 올리면 GitHub Actions가 빌드와 테스트를 돌려요. 통과한 뒤 `main`에 합쳐요.
- PR은 다른 BE 한 명이 확인하고 합쳐요. 합친 브랜치는 삭제해요.
- 3주짜리 해커톤이라 `develop`, `release` 브랜치는 두지 않아요.

## **API 규칙**

FE와 같이 보는 약속이에요. 바꿀 때는 FE와 먼저 이야기해요.

### **주소**

- 명사 복수형, 소문자, 여러 단어는 하이픈: `/sources`, `/places`, `/plans/{planId}/days`
- 동작은 HTTP 메서드로 나타내요. 주소에 `get`, `update` 같은 동사를 넣지 않아요.

| **메서드** | **쓰는 경우** | **예** |
| --- | --- | --- |
| `GET` | 조회 | `GET /places?category=FOOD` |
| `POST` | 새로 만들기 | `POST /sources` |
| `PATCH` | 일부만 수정 | `PATCH /places/{placeId}` |
| `PUT` | 통째로 바꾸기 | `PUT /plans/{planId}/days/{dayNumber}` (그 날의 노드 순서 전체 저장) |
| `DELETE` | 삭제 | `DELETE /plans/{planId}` |

### **화면별 API 초안**

와이어프레임을 보고 뽑은 초안이에요. 금요일 회의에서 FE와 확정하고, 확정되면 노션 API 명세로 옮겨요.

| **화면** | **API** | **내용** |
| --- | --- | --- |
| S0-1b 회원가입 | `POST /auth/email-codes`, `POST /auth/email-codes/verify`, `POST /auth/signup` | 6자리 인증번호 발송·확인, 가입 후 바로 로그인 토큰 발급 |
| S0-1a 로그인 | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/password-reset` | 로그인 상태 유지는 리프레시 토큰으로 |
| S0-2 메뉴바 | `GET /users/me` | 닉네임, 보관함 카드 수, 여행목록 수 |
| S1-2 링크 입력 | `POST /sources`, `GET /sources/{sourceId}` | 여러 링크를 한 번에 분석, 링크별 상태와 카드 초안 |
| S2-1 생성 결과 | `POST /places` (여러 장) | "모두 저장"을 누르면 그때 보관함에 저장 |
| S3-1 보관함 | `GET /places?category=` | 카테고리별 개수 포함 |
| S3-2·S3-3 카드 | `GET`·`PATCH`·`DELETE /places/{placeId}`, `POST /places` | 직접 추가도 같은 `POST /places` |
| S2-2·S3-3 사진 | `POST /images` | 사진 올리기 → Cloud Storage 주소 돌려줌 |
| S3-3 장소 검색 | `GET /place-search?query=` | Google Maps로 이름 → 현지 주소·좌표·사진 |
| S4-0 계획 홈 | `GET /plans?status=DRAFT` | 작성 중인 계획과 진행률(완성 일자 / 전체 일자) |
| S4-1·S4-2 생성 | `POST /plans` | 여행명·기간·장소. 기간에 맞춰 1일차~n일차 자동 생성 |
| S4-3 숙소 선택 | `PUT /plans/{planId}/stays` | 고른 숙소 카드 목록 (없으면 빈 목록) |
| S4-4 구역 선택 | `PUT /plans/{planId}/days/{dayNumber}/areas` | 그 날 둘러볼 구역 이름 목록 |
| S4-5 동선 짜기 | `PUT /plans/{planId}/days/{dayNumber}` | 확정한 노드 순서, 플랜B, 그 날 완성 여부 |
| S4-7 계획 보기 | `GET /plans/{planId}` | 일자별 노드·플랜B·숙소. 일자 순서 바꾸기는 `PATCH /plans/{planId}/days/order` |
| S4-7a·b 경로 | `GET /routes?from=&to=&departAt=` | 두 장소 사이 대중교통 최단 경로 |
| S4-8 공유 | `POST /plans/{planId}/share-links` | 읽기 전용 / 함께 편집 링크 발급 |
| S5-1 여행목록 | `GET /plans?owner=me`, `GET /plans?owner=shared`, `POST /plans/{planId}/copies` | 내 계획·공유받은 계획, 복제 |

### **요청·응답 형식**

- JSON 필드는 카멜 케이스: `placeId`, `createdAt`
- 필드 이름은 DB 컬럼 이름을 기준으로 해요: `nickname` 컬럼 → `nickname`
- PK는 항상 도메인명을 붙여요: `id` (X) → `placeId`, `planId` (O)
- 요청에서는 주소에 이미 도메인이 있으면 필드에 도메인명을 붙이지 않아요. PK만 예외예요.
    - `POST /plans` → `{ "name": "오사카 3박 4일" }` (`planName`으로 받지 않음)
- 응답에 같은 이름의 필드가 둘 이상 나오면 앞에 역할을 붙여요: `ownerNickname`, `memberNickname`
- 다른 엔티티의 값을 내려줄 때는 그 PK도 같이 내려줘요. 화면에 닉네임만 나와도 `userId`를 같이 줘요.
- 목록은 `{도메인명}s` 필드에 담아요. `placeList`처럼 자료형을 이름에 넣지 않아요. 목록 안 객체의 필드에는 도메인명을 생략하고 PK만 붙여요.

```json
{
  "places": [
    { "placeId": 1, "name": "이치란 라멘", "category": "FOOD" }
  ]
}
```

- Enum은 `.name()` 값 그대로 내려줘요: `"category": "FOOD"`, `"status": "DRAFT"`. 화면 이름("맛집")으로 바꾸는 건 FE가 해요.
- 페이지가 필요한 목록(보관함 카드, 여행목록)은 쿼리 이름을 `page`, `size`, `sortType`으로 써요. `page`는 0부터 시작해요 (Spring 기본값).
- 날짜는 `"2026-11-14"`, 날짜·시간은 ISO 8601 문자열: `"2026-11-14T10:24:00+09:00"`
- 가격은 현지 통화로 받아요. 숫자와 통화 코드를 따로 둬요: `{ "amount": 1200, "currency": "JPY", "isFrom": true }` (`isFrom`은 "¥1,200~"처럼 "부터" 표시)
- 성공하면 데이터를 그대로 내려줘요. 감싸는 껍데기(`{ success, data }`)는 두지 않아요.
- 실패하면 항상 같은 모양으로 내려줘요.

```json
{
  "code": "DUPLICATE_EMAIL",
  "message": "이미 가입된 이메일이에요. 로그인해 주세요."
}
```

- `code`는 FE가 분기할 때 쓰는 값이라 바꾸지 않아요. `message`는 와이어프레임에 적힌 문구를 그대로 써서 화면에 바로 띄울 수 있게 해요.

| **code** | **message** | **화면** |
| --- | --- | --- |
| `DUPLICATE_EMAIL` | 이미 가입된 이메일이에요. 로그인해 주세요. | S0-1b |
| `INVALID_EMAIL_CODE` / `EMAIL_CODE_EXPIRED` | 인증번호를 다시 확인해 주세요. / 인증 시간이 지났어요. | S0-1b |
| `INVALID_LOGIN` | 이메일 또는 비밀번호를 확인해주세요 | S0-1a |
| `SOURCE_FETCH_FAILED` | 링크가 비공개이거나 지원하지 않는 형식일 수 있어요. | S1-3 |
| `INVALID_PLAN_PERIOD` | 여행 기간을 다시 확인해주세요 | S4-1 |
| `ROUTE_NOT_FOUND` | 경로 정보를 불러오지 못했어요 | S4-7a |
| `PLAN_FORBIDDEN` | 이 계획을 볼 수 있는 권한이 없어요. | S4-8 |

| **상태 코드** | **쓰는 경우** |
| --- | --- |
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 만들기 성공 |
| `202 Accepted` | 링크 분석을 시작함 (결과는 나중에 조회) |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 요청 값이 잘못됨 (기간 오류, 필수 항목 빠짐 등) |
| `401 Unauthorized` | 로그인 안 함, 토큰 만료 |
| `403 Forbidden` | 읽기 전용으로 공유받은 계획을 고치려 함 |
| `404 Not Found` | 대상이 없거나, 다른 사람의 카드·계획임 (있다는 것도 알려주지 않아요) |
| `409 Conflict` | 이미 가입된 이메일 |
| `500 Internal Server Error` | 서버 오류 |

### **링크 분석 흐름**

AI 분석은 몇 초~수십 초 걸려서 요청 한 번에 끝내지 않아요.

1. FE가 `POST /sources`로 링크 여러 개를 한 번에 보내면 BE는 바로 `202`와 `sourceId`를 돌려주고 분석을 시작해요.
2. FE는 `GET /sources/{sourceId}`를 몇 초마다 불러 상태를 확인해요.
3. 링크마다 상태가 `PENDING` → `ANALYZING` → `DONE` 또는 `FAILED`로 바뀌어요. 전체 상태는 링크가 모두 끝나면 끝나요.
4. `DONE`인 링크는 카드 초안 목록을, `FAILED`인 링크는 실패 이유(`code`, `message`)와 원래 링크를 같이 내려줘요. 일부만 실패해도 성공한 카드는 보여줘요 (S1-3 "링크 여러 개 중 일부만 실패").
5. 카드 초안은 보관함에 바로 넣지 않아요. 사용자가 S2-1에서 고친 뒤 "모두 저장"을 누르면 `POST /places`로 저장해요.
- AI가 일부 항목을 못 채웠어도 카드는 만들어요. 빈 필수 항목은 `null`로 내려서 FE가 "빈 항목"으로 표시하게 해요.
- 하나의 링크에서 장소 카드와 교통·팁 카드가 같이 나올 수 있어요.

### **요약카드**

와이어프레임 S2-0 카드 포맷을 따라요.

| **카테고리 키** | **화면 이름** | **핵심 포인트 이름** | **지도 노드** |
| --- | --- | --- | --- |
| `FOOD` | 맛집 | 대표메뉴 | O |
| `CAFE` | 카페 | 대표메뉴 | O |
| `STAY` | 숙소 | 특징 | O (네모 노드) |
| `SIGHTSEEING` | 관광지 | 볼거리 | O |
| `ACTIVITY` | 액티비티 | 즐길거리 | O |
| `SHOPPING` | 쇼핑 | 대표상품 | O |
| `TRANSPORT` | 교통 | (항목 따로) | X |
| `TIP` | 팁 | (항목 따로) | X |

- 카테고리는 `Category` enum 하나로 두고, 지도 노드인지는 enum에 `isPlace()`로 둬요. 키 이름은 FE `categories.ts`와 같아야 해요. 하나라도 바꾸면 FE에 알려요.
- **장소 카드 필수:** 사진, 카테고리, 장소명, 위치(현지 주소 + 위도·경도), 원본링크
    - 원본링크는 AI로 만든 카드에서는 필수, 직접 추가한 카드에서는 선택이에요 (S3-3).
    - 영상 링크는 장소가 나오는 타임스탬프를 같이 저장해요 (S3-2).
- **장소 카드 선택:** 핵심 포인트(최대 3개), 가격, 메모
- **교통·팁 카드:** 세부유형(예: 교통패스, 환전), 제목, 적용지역, 비용, 혜택, 방법, 장소, 주의. 값이 없는 항목은 `null`로 두고 FE가 숨겨요.
- 핵심 포인트 최대 3개, 필수 항목은 서버에서도 검사해요. FE 검사만 믿지 않아요.

### **여행계획**

- 계획은 여행명, 시작일·종료일, 여행 장소(도시), 상태(`DRAFT` 작성 중 / `COMPLETED` 완성)를 가져요. 일자(1일차~n일차)는 기간으로 서버가 만들어요.
- 계획을 만들 때 여행 장소에 해당하는 보관함 장소 카드를 불러와요 (S4-2 "보관함 노드 27개 불러옴"). 0개여도 계획은 만들어요.
- 일자마다 숙소(출발·도착지), 구역 목록, 확정한 노드 순서, 완성 여부를 가져요. 진행률은 완성 일자 수 / 전체 일자 수예요.
- 노드 순서는 정수 `orderIndex`로 저장하고, 그 날의 순서를 통째로 받아 다시 매겨요. 하나씩 끼워 넣는 계산은 하지 않아요.
- 플랜B는 "어느 노드 대신 갈 곳"이에요. 대신할 노드 id와 장소 카드 id를 저장해요. 대신할 노드가 계획에서 빠지면 그 플랜B도 같이 지워요.
- 같은 장소 카드는 한 계획 안에서 한 번만 확정할 수 있어요 (S4-5c-3 "다른 일자에 이미 넣은 곳도 회색").
- AI 초안 만들기(S4-2)는 우선순위 '하'예요. 직접 만들기가 다 된 뒤에 시작해요.

### **경로**

- 구간 경로는 Google Maps 경로 API로 대중교통·도보·택시 중 가장 빠른 경로 하나만 내려줘요 (S4-7a).
- 출발 시각은 계획에 시간이 있으면 그 시각, 없으면 현재 시각이에요.
- 같은 두 장소·같은 시간대 결과는 잠깐 저장해 두고 다시 써요. Google Maps 호출 비용을 줄여요.
- 응답에는 총 소요시간, 출발·도착 시각, 수단별 구간(도보 4분, 지하철 [노선명] 12분…), 환승 수, 요금, 단계별 안내를 담아요.

### **공유**

- 공유 링크는 `[서비스 주소]/plan/{planId}` 모양이에요 (S4-8). 링크를 만들 때 읽기 전용 / 함께 편집을 골라요.
- 읽기 전용 링크는 로그인하지 않아도 볼 수 있어요. 함께 편집은 로그인한 사람만, 링크로 들어오면 그 계획의 편집 멤버로 추가해요.
- 계획 id를 그대로 주소에 쓰니, id를 아는 것만으로 열람되면 안 돼요. 공유를 켠 계획만 비회원이 볼 수 있게 해요.

## **회원·인증**

- 이메일 계정만 써요. SNS 로그인은 만들지 않아요 (S0-1).
- **가입:** 인증번호 받기 → 메일로 6자리 발송, 유효시간 5분 → 확인 → 비밀번호(영문·숫자 포함 8자 이상)·닉네임·필수 약관 2개 동의 → 가입 후 바로 로그인 (S0-1b)
- 인증번호는 메모리가 아니라 DB에 만료 시각과 함께 저장해요. Cloud Run 인스턴스가 여러 개여도 같은 결과가 나와야 해요.
- 비밀번호는 BCrypt로 저장해요. 비밀번호·토큰·인증번호는 로그에 남기지 않아요.
- 로그인하면 액세스 토큰(짧게)과 리프레시 토큰(길게, "로그인 상태 유지")을 내려줘요.
- 비밀번호 찾기는 가입한 이메일로 재설정 링크를 보내요. 가입 안 된 이메일이어도 같은 응답을 내려서 가입 여부를 알려주지 않아요.
- 마케팅 수신 동의(선택)는 저장만 하고 쓰지 않아요.
- `/auth/**`, 읽기 전용 공유 계획 조회, `/actuator/health`만 로그인 없이 열고 나머지는 모두 로그인이 필요해요.
- 카드·계획은 모두 만든 사람의 것이에요. 서비스에서 조회할 때 항상 "내 것인지, 공유받은 것인지"를 확인해요. 컨트롤러에서 받은 id만 믿고 조회하지 않아요.

## **코드 규칙**

### **이름 짓기**

- 클래스는 파스칼 케이스: `PlaceService`
- 변수·메서드는 카멜 케이스: `placeId`, `movePlaceToDay`
- 상수는 대문자 스네이크 케이스: `MAX_KEY_POINTS`, `EMAIL_CODE_TTL_MINUTES`
    - `final`로 선언했다고 모두 상수는 아니에요. 앱 어디서도 바뀌지 않는 값만 `static final` 상수로 써요.
- 패키지는 소문자 한 단어: `place`, `plan`
- Boolean은 `is`, `has`, `can`으로 시작하고 부정형은 피해요: `isCompleted` (O), `isNotCompleted` (X)
- 메서드 이름만 보고 무엇을 하는지 알 수 있게 지어요: `startAnalysis`, `confirmNode`, `addPlanB`
- DB 테이블과 컬럼은 스네이크 케이스: `place`, `plan_node`, `created_at`
- 화면에 나오는 이름과 코드 이름을 맞춰요: 요약카드 → `Place`, 여행계획 → `Plan`, 일자 → `PlanDay`, 노드 → `PlanNode`, 플랜B → `PlanAlternative`, 숙소 선택 → `PlanStay`

| **역할** | **이름 규칙** | **예** |
| --- | --- | --- |
| 컨트롤러 | `~Controller` | `PlaceController` |
| 서비스 | `~Service` | `PlaceService` |
| 레포지토리 | `~Repository` | `PlaceRepository` |
| 엔티티 | 접미사 없이 | `Place`, `PlanNode` |
| 요청 DTO | `~Request` | `PlaceUpdateRequest` |
| 응답 DTO | `~Response` | `PlaceResponse` |

### **계층별 메서드 이름**

컨트롤러·서비스와 레포지토리의 메서드 이름을 다르게 지어서, 비즈니스 로직인지 DB 접근인지 이름만 보고 알 수 있게 해요.

| **하는 일** | **컨트롤러·서비스** | **레포지토리** |
| --- | --- | --- |
| 조회 | `getPlace`, `getPlaces` | `findByPlaceId`, `countByCategory`, `existsByEmail` |
| 만들기 | `createPlace` | `save` (JPA 기본) |
| 수정 | `modifyPlace` | 엔티티 값을 바꾸고 변경 감지로 저장 |
| 삭제 | `removePlace` | `delete` (JPA 기본) |

- 하는 일을 더 정확히 말할 수 있으면 `get`, `create`, `modify`, `remove` 대신 그 이름을 써요: `signUp`, `startAnalysis`, `confirmNode`, `addPlanB`
- 레포지토리는 Spring Data JPA 이름 규칙(`findBy`, `countBy`, `existsBy`)을 따라요. 직접 만든 쿼리도 같은 접두사로 시작해요.

### **계층별 역할**

- **컨트롤러:** 요청을 받고 응답을 돌려주는 일만 해요. 로직은 서비스에 둬요.
- **서비스:** 비즈니스 로직과 권한 확인을 둬요. 여러 테이블을 바꾸는 일은 `@Transactional`로 묶어요.
- **레포지토리:** DB 조회·저장만 해요.
- 엔티티를 그대로 응답으로 내보내지 않아요. 항상 응답 DTO로 바꿔서 내려줘요. 특히 `User`의 비밀번호가 응답에 섞이지 않게 해요.
- DTO는 `record`로 만들어요.

### **예외 처리**

- 예외는 `global/exception`의 `ErrorCode` enum과 `BusinessException` 하나로 다뤄요: `throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);`
- `GlobalExceptionHandler`가 모든 예외를 위의 실패 응답 모양으로 바꿔요. 컨트롤러마다 `try-catch`를 쓰지 않아요.
- 예상하지 못한 예외도 `500`과 같은 모양으로 내려주고, 원인은 로그에만 남겨요. 에러 메시지에 내부 정보(쿼리, 키, 경로)를 넣지 않아요.

### **설정**

- 프로필은 `local`, `prod` 두 개예요. `local`은 내 컴퓨터의 MySQL(또는 Docker), `prod`는 Cloud SQL에 연결해요.
- 서버 포트는 Cloud Run이 넘겨주는 `PORT` 환경 변수를 따라요: `server.port: ${PORT:8080}`
- CORS는 FE 배포 주소, Vercel 미리보기 주소, `http://localhost:5173`만 허용해요.

## **폴더 구조**

```
src/main/java/com/reeltrip/
├── ReeltripApplication.java
│
├── domain/                         # 기능 단위 모듈
│   ├── auth/                       # 가입·로그인·인증번호·비밀번호 재설정 (S0-1)
│   │   ├── controller/AuthController.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   └── EmailCodeService.java
│   │   ├── entity/EmailCode.java
│   │   └── dto/
│   │
│   ├── user/                       # 회원 정보 (S0-2)
│   │   ├── controller/UserController.java     # GET /users/me
│   │   ├── service/UserService.java
│   │   ├── repository/UserRepository.java
│   │   ├── entity/User.java
│   │   └── dto/
│   │
│   ├── source/                     # 링크 등록과 분석 상태 (S1)
│   │   ├── controller/SourceController.java   # POST /sources, GET /sources/{id}
│   │   ├── service/
│   │   │   ├── SourceService.java
│   │   │   └── AnalysisService.java           # 내용 가져오기 → AI 요약 → 카드 초안
│   │   ├── repository/
│   │   ├── entity/
│   │   │   ├── Source.java                    # 분석 요청 한 번
│   │   │   ├── SourceLink.java                # 링크 하나와 그 상태
│   │   │   └── AnalysisStatus.java            # PENDING, ANALYZING, DONE, FAILED
│   │   └── dto/
│   │
│   ├── place/                      # 요약카드와 보관함 (S2, S3)
│   │   ├── controller/
│   │   │   ├── PlaceController.java           # /places
│   │   │   └── PlaceSearchController.java     # GET /place-search
│   │   ├── service/PlaceService.java
│   │   ├── repository/PlaceRepository.java
│   │   ├── entity/
│   │   │   ├── Place.java
│   │   │   └── Category.java                  # 장소 6개 + 교통·팁
│   │   └── dto/
│   │
│   ├── plan/                       # 여행계획 (S4, S5)
│   │   ├── controller/PlanController.java
│   │   ├── service/
│   │   │   ├── PlanService.java
│   │   │   ├── PlanEditService.java           # 숙소·구역·노드 확정·플랜B
│   │   │   └── PlanShareService.java          # 공유 링크·멤버·권한
│   │   ├── repository/
│   │   ├── entity/
│   │   │   ├── Plan.java
│   │   │   ├── PlanDay.java
│   │   │   ├── PlanStay.java
│   │   │   ├── PlanNode.java
│   │   │   ├── PlanAlternative.java           # 플랜B
│   │   │   └── PlanMember.java                # OWNER, EDITOR, VIEWER
│   │   └── dto/
│   │
│   ├── route/                      # 구간 경로 (S4-7a, S4-7b)
│   │   ├── controller/RouteController.java
│   │   ├── service/RouteService.java
│   │   └── dto/
│   │
│   └── image/                      # 사진 올리기 (S2-2, S3-3)
│       ├── controller/ImageController.java
│       └── service/ImageService.java
│
├── infra/                          # 외부 서비스 연결
│   ├── ai/LlmClient.java           # LLM API 호출, 프롬프트
│   ├── fetcher/                    # 릴스·유튜브·블로그 링크에서 내용 가져오기
│   ├── map/                        # Google Maps 장소 검색·경로
│   ├── mail/MailSender.java        # 인증번호·비밀번호 재설정 메일
│   └── storage/StorageClient.java  # Cloud Storage
│
└── global/                         # 여러 기능이 같이 쓰는 것
    ├── config/                     # CORS, 비동기 실행, 외부 API 설정
    ├── security/                   # Spring Security, JWT 필터, 로그인 사용자 꺼내기
    ├── exception/
    │   ├── ErrorCode.java
    │   ├── BusinessException.java
    │   ├── ErrorResponse.java
    │   └── GlobalExceptionHandler.java
    └── common/BaseTimeEntity.java  # createdAt, updatedAt

src/main/resources/
├── application.yml                 # 공통 설정
├── application-local.yml           # 로컬 설정 (커밋하지 않음)
└── application-prod.yml            # 배포 설정 (값은 환경 변수로)

Dockerfile                          # Cloud Run에 올릴 이미지
.github/workflows/
├── ci.yml                          # PR마다 빌드·테스트
└── deploy.yml                      # main에 합쳐지면 Cloud Run 배포
```

- 외부 서비스를 부르는 코드는 `infra`에만 둬요. 서비스는 `infra`의 클래스를 통해서만 외부를 불러요. 그래야 링크 종류가 늘거나 AI 모델·지도 API를 바꿔도 한 곳만 고치면 돼요.
- 한 기능에서만 쓰는 건 그 기능 폴더에 두고, 두 곳 이상에서 쓰게 되면 `global`로 옮겨요.

## **배포 (GCP)**

| **항목** | **쓰는 것** | **설정** |
| --- | --- | --- |
| 서버 | Cloud Run | 리전 `asia-northeast3` (서울) |
| DB | Cloud SQL for MySQL | 같은 리전, Cloud Run에서 Cloud SQL 연결로 접속 |
| 사진 | Cloud Storage | 같은 리전 버킷. 공개 읽기, 쓰기는 서버만 |
| 비밀 값 | Secret Manager | DB 비밀번호, JWT 비밀 키, LLM·Google Maps API 키, 메일 비밀번호. Cloud Run 환경 변수로 연결 |
| 이미지 저장 | Artifact Registry | 같은 리전 |
| 자동 배포 | GitHub Actions | GCP 인증은 키 파일 대신 Workload Identity 연결로 |

- **CPU 항상 할당을 켜요.** 기본 설정은 응답을 보낸 뒤 CPU를 거의 주지 않아서, `202`를 돌려준 뒤 돌아가는 링크 분석이 멈추거나 아주 느려져요.
- **최소 인스턴스는 1로 둬요.** 0이면 한동안 안 쓰다가 첫 요청이 올 때 서버를 새로 켜느라 몇 초씩 걸려요. 심사 중에 첫 화면이 느려지지 않게 해요.
- **최대 인스턴스는 작게(예: 3) 둬요.** Cloud SQL 연결 수와 LLM·Google Maps 비용이 갑자기 늘지 않게 해요.
- 분석 상태, 인증번호처럼 여러 요청에 걸치는 값은 메모리가 아니라 DB에만 저장해요. 인스턴스가 여러 개거나 재시작돼도 같은 결과를 내려줘야 해요.
- 올린 사진은 서버 디스크가 아니라 Cloud Storage에 둬요. Cloud Run 디스크는 재시작하면 사라져요.
- Google Maps API 키는 사용할 API(장소 검색, 경로)만 켜고 하루 사용량 한도를 걸어 둬요.
- 배포 후에는 `GET /actuator/health`로 서버가 떠 있는지 확인해요 (`spring-boot-starter-actuator` 필요). 문제가 생기면 Cloud Run에서 바로 전 버전으로 트래픽을 돌려요.
- 무료 크레딧과 비용 때문에 Cloud SQL은 가장 작은 사양으로 시작하고, 데모데이 뒤에는 인스턴스를 멈춰요.

## **테스트**

- 해커톤이라 모든 코드에 테스트를 쓰지는 않아요. 아래는 꼭 써요.
    - 노드 확정·순서 바꾸기·플랜B 추가·제거, 일자 순서 바꾸기처럼 틀리기 쉬운 계획 로직
    - AI 응답을 카드 초안으로 바꾸는 부분 (응답 형식이 깨졌거나 일부 항목이 비었을 때 포함)
    - 권한: 다른 사람의 카드·계획은 `404`, 읽기 전용 공유 계획 수정은 `403`
    - 인증번호 만료·틀림, 중복 가입
- 테스트에서 실제 LLM·Google Maps·메일·Cloud Storage를 부르지 않아요. 가짜 응답으로 바꿔서 확인해요.

### **테스트 작성법**

- `@DisplayName`은 "~테스트"가 아니라 결과까지 담은 문장으로 써요.
    - `플랜B 추가 테스트` (X)
    - `확정한 노드에 플랜B를 추가하면 그 노드 대신 갈 곳으로 저장된다.` (O)
- 메서드 입장이 아니라 서비스 규칙의 말로 써요.
    - `orderIndex가 겹치면 실패한다.` (X)
    - `한 계획 안에서 같은 장소는 두 번 확정할 수 없다.` (O)
- 테스트 본문은 Given / When / Then 세 부분으로 나누고 주석으로 표시해요.

```java
@Test
@DisplayName("읽기 전용으로 공유받은 계획을 수정하면 권한 없음으로 실패한다.")
void modifyPlan_viewer_forbidden() {
    // given: 읽기 전용 멤버가 있는 계획
    // when: 그 멤버가 일자 순서를 바꾸면
    // then: PLAN_FORBIDDEN 예외가 난다
}
```
- PR 전에 `./gradlew test`가 통과해야 해요.

## **이슈·PR 템플릿**

`.github/ISSUE_TEMPLATE/`과 `.github/pull_request_template.md`에 넣어요.

### **버그 이슈**

```
---
name: 버그
about: 버그 신고
title: '[버그] '
labels: bug
---

## 무슨 문제인가요

## 재현 방법
<!-- 호출한 API, 요청 값, 관련 화면 번호(예: S4-5c) -->
1.
2.

## 기대한 동작

## 실제 응답·로그
<!-- 배포 서버인지 로컬인지 -->
```

### **기능 이슈**

```
---
name: 기능
about: 만들 기능
title: '[기능] '
labels: enhancement
---

## 만들 것
<!-- 관련 화면 번호(예: S1-2, S2-1) -->

## 할 일
- [ ]
- [ ]

## 참고
<!-- 와이어프레임, 기능 명세, API 명세, 관련 FE 이슈 -->
```

### **PR**

```
## 무엇을 했나요
<!-- 관련 화면 번호 -->

## API·DB 변경
<!-- 바뀐 API 주소·요청·응답, 바뀐 테이블·컬럼. 없으면 지워 주세요 -->

## 정한 것
<!-- 와이어프레임 "확인 필요" 항목을 어떻게 정했는지. 없으면 지워 주세요 -->

## 체크리스트
- [ ] 커밋 메시지 규칙을 지켰어요
- [ ] `./gradlew test`가 통과해요
- [ ] 로컬에서 성공·실패 응답을 모두 확인했어요
- [ ] 로그인 안 함·다른 사람 데이터 접근을 막는지 확인했어요
- [ ] 새 환경 변수·비밀 값이 있다면 Cloud Run과 Secret Manager에 추가했어요
- [ ] API가 바뀌었다면 FE에 알리고 노션 API 명세를 고쳤어요

resolves #
