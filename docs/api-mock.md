# BE1 가짜 API 서버 (#8)

이 서버는 FE가 실제 구현 전 화면과 요청·응답을 확인하는 용도예요. Java 21 / Spring Boot 3.5.16을 사용하고, 기존 API 주소와 필드 이름을 유지해요. 문서상의 초안과 실제 FE 합의는 구분해요.

## 실행

Java 21의 JAVA_HOME을 설정한 뒤 저장소 루트에서 실행해요.

```powershell
.\gradlew.bat bootMockRun
```

macOS/Linux는 `./gradlew bootMockRun`이에요. 기본 주소는 `http://localhost:8081`, 다른 포트는 `bootMockRun --args="--server.port=8090"`으로 바꿔요.

DB, JWT 비밀 키, Gemini·Maps·메일·Storage 계정은 필요하지 않아요. CORS는 기존 설정을 재사용해 localhost:5173을 허용하고, 다른 FE 주소는 CORS_ALLOWED_ORIGINS로 설정해요.

- `src/mock`와 `com.reeltripmock` 패키지는 운영 코드와 분리돼요. 운영 `bootRun`과 `bootJar`에는 가짜 API·가짜 이미지가 포함되지 않아요.
- `bootMockRun`은 `api-mock` 프로필을 추가하고 `prod`가 함께 켜지면 시작을 거부해요.
- 가짜 서버에는 실제 사용자·비밀번호·개인 사진을 보내지 않아요. 메일·파일 저장·외부 API 호출은 없어요.
- 링크 접수 내역은 이 프로세스 메모리에만 있고 재시작하면 사라져요. 실제 서버의 DB 지속성·인증·비동기 처리 구현을 대신하지 않아요.
- BE2의 카드·계획 API는 이 별도 서버에 없어요. 가짜 토큰과 sourceLinkId는 실제 서버에서 사용할 수 없어요.

## 고정 입력과 인증

| 항목 | 가짜 서버 값 |
| --- | --- |
| 예시 이메일 | demo@example.test |
| 로그인 비밀번호 | Mockpass123 |
| 이메일 인증번호 | 123456 |
| accessToken | mock-access-token |
| refreshToken | mock-refresh-token |
| 재설정 token | mock-reset-token |
| 회원 ID / 닉네임 | 1001 / 테스트 |

인증 API와 `/actuator/health`, 가짜 이미지 `/mock/place.svg`는 토큰 없이 호출해요. 나머지 API는 `Authorization: Bearer mock-access-token`을 보내요. 잘못된 토큰을 보내면 공개 API라도 `401 UNAUTHORIZED`예요.

가입은 입력 형식만 검사해요. 이메일 인증 여부·실제 계정 중복·시간 만료·토큰 1회 사용 등 실제 비즈니스 규칙은 이후 인증 이슈에서 구현해요.

## API 15개 대조

요청 JSON과 상세 응답은 [API 명세](api-spec.md), 실행 가능한 요청은 [be1-mock.http](be1-mock.http)에 있어요. 성공 응답은 껍데기 없이, 실패는 `{code,message}`로 내려요.

| API | 성공 | 핵심 응답 |
| --- | --- | --- |
| POST /auth/email-codes | 200 | expiresAt, resendAvailableAt |
| POST /auth/email-codes/verify | 200 | email, signupAvailableUntil |
| POST /auth/signup | 201 | userId, nickname, accessToken, refreshToken |
| POST /auth/login | 200 | 가입 응답과 같음 |
| POST /auth/refresh | 200 | accessToken, refreshToken |
| POST /auth/password-reset | 200 | 빈 본문, 가입 여부와 무관 |
| POST /auth/password-reset/verify | 200 | maskedEmail, expiresAt |
| POST /auth/password-reset/confirm | 204 | 빈 본문 |
| GET /users/me | 200 | userId, email, nickname, placeCount, planCount |
| GET /sources | 200 | sources 배열, page=0 / size=10 기본 |
| POST /sources | 202 | sourceId, status=PENDING |
| GET /sources/{sourceId} | 200 | title, status, sourceLinks와 places 또는 code/message |
| POST /images | 201 | imageUrl, multipart file |
| GET /place-search | 200 | places, googlePlaceId와 주소·좌표·사진 |
| GET /routes | 200 | totalMinutes, departAt, arriveAt, segments |

가짜 사진 업로드는 PNG/JPEG/WebP와 5MB까지만 받으며 가짜 이미지 주소를 반환해요. 이 상한은 가짜 서버 실행을 위한 값이고 운영 업로드 계약은 #21에서 확인해요. 검색어는 최대 200자, 목록 size는 최대 100으로 제한했으며 운영 확정값으로 간주하지 않아요.

## 성공·부분 실패·전체 실패

`GET /sources/1`은 성공, `/sources/2`는 일부 성공, `/sources/3`은 전체 실패예요.

`POST /sources`에 `X-Mock-Scenario` 헤더를 보내 응답을 골라요.

| 헤더 값 | 상세 조회 결과 |
| --- | --- |
| success 또는 생략 | 모든 링크 DONE |
| partial | 첫 링크 DONE, 나머지 FAILED (링크 2개 이상 필요) |
| failed | 모든 링크 FAILED |
| analyzing | ANALYZING 유지, title=null, 카드 없음 |

POST 응답은 PENDING이고, 다음 GET부터 위 고정 결과를 반환해요. 실제 작업 지연·백그라운드 실행·재시도는 없어요. 성공 카드 두 장은 기존 `FakeLlmClient`의 FOOD / TRANSPORT를 재사용하며, 장소 사진·좌표만 가짜로 채워요. 영상 시점·원본 링크·sourceLinkId를 유지하고 placeId는 만들지 않아요.

`GET /place-search`에서 `X-Mock-Scenario: empty`를 보내면 빈 목록이에요.

## 실패 화면 재현

가짜 서버에서만 `X-Mock-Error: 에러코드`를 보내면 기존 ErrorCode의 상태·메시지를 반환해요. 이 헤더는 운영 API 계약에 추가하지 않아요.

```http
POST http://localhost:8081/auth/email-codes
Content-Type: application/json
X-Mock-Error: DUPLICATE_EMAIL

{"email":"demo@example.test"}
```

지원 값: INVALID_INPUT, DUPLICATE_EMAIL, INVALID_EMAIL_CODE, EMAIL_CODE_EXPIRED, EMAIL_CODE_TOO_FREQUENT, EMAIL_CODE_ATTEMPTS_EXCEEDED, EMAIL_NOT_VERIFIED, INVALID_PASSWORD_FORMAT, MAIL_SEND_FAILED, INVALID_RESET_TOKEN, RESET_TOKEN_EXPIRED, INVALID_LOGIN, ROUTE_NOT_FOUND, INTERNAL_SERVER_ERROR.

일부 링크 실패의 SOURCE_FETCH_FAILED는 전체 HTTP 실패가 아니라 링크별 결과에 담겨요. 실제 로그인 비밀번호가 예시와 다르면 INVALID_LOGIN, 인증번호가 다르면 INVALID_EMAIL_CODE, 재설정 토큰이 다르면 INVALID_RESET_TOKEN이에요.

## FE 합의가 남은 항목

아래는 이번 구현에서 확정한 운영 정책이 아니에요. 외부 팀원에게 메시지를 보내거나 합의가 완료됐다고 기록하지 않았어요.

| 항목 | 현재 사용한 기준 / 확인할 것 |
| --- | --- |
| 인증·가입 화면 | 인증번호와 필수 약관 2개 동의 유지. 디자인·FE 동선 확인 필요 |
| 비밀번호 찾기 화면 | 메일 요청·token 확인·새 비밀번호 화면, FE 경로 확인 필요 |
| 메일 발송 서비스 | 계정·서비스 미정. #10에서 연결 |
| 재설정 FE 주소 | 문서 예시는 /password-reset?token=. 실제 배포 주소 미정 |
| 로그아웃·refresh 회전 | 현재 API 15개에는 로그아웃 없음. 회전·폐기 계약은 #12에서 확인 |
| 가격 | 최신 decisions.md의 priceText 사용. 기존 컨벤션의 amount/currency/isFrom과 차이를 FE에 확인 |
| 경로 | 최신 결정대로 간단 segments만 반환. 요금·환승 수·단계별 안내 제외 |
| 시간·에러 문구 | 기존 명세 기준 +09:00, 제안 표시 문구는 FE 확인 필요 |
| 실시간 상태·목록 정렬 | 가짜 서버의 고정 예시는 이후 실제 분석·조회 동작과 구분 |
| 사진 업로드·검색 제한 | 가짜 서버 실행값이며 운영 정책은 해당 이슈에서 확인 |

## 검증

```powershell
.\gradlew.bat test verifyMockIsolation
```

`verifyMockIsolation`은 운영 bootJar를 열어 com/reeltripmock 클래스·가짜 이미지·가짜 설정이 없는지 확인해요. 계약 테스트는 15개 API와 인증 실패·부분 실패·입력 오류·DB/실제 Gemini 미생성을 확인해요.

