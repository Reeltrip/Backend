# 로그인·토큰 재발급 (#12)

Java 21 / Spring Boot 3.5.16에서 실제 인증을 처리해요. 메일·회원가입 구현과 분리돼 있으며, DB에 있는 BCrypt 계정으로 로그인할 수 있어요. 새 라이브러리는 추가하지 않았어요.

## POST /auth/login (S0-1a)

```json
{"email":"traveler@example.test","password":"비밀번호"}
```

성공은 200과 아래 필드예요. Cache-Control: no-store / Pragma: no-cache를 내려요.

```json
{"userId":1,"nickname":"여행자","accessToken":"...","refreshToken":"..."}
```

- 없는 이메일과 틀린 비밀번호는 동일한 401 INVALID_LOGIN이에요.
- 없는 이메일도 가짜 BCrypt 해시와 비교해 계정 유무에 따른 처리 시간 차이를 줄여요.
- 누락·빈 값·잘못된 이메일 등 입력 오류는 기존 400 INVALID_INPUT이에요.
- BCrypt의 72바이트를 넘는 비밀번호는 401 INVALID_LOGIN으로 처리해요.
- 액세스 JWT는 기존 설정대로 30분, 리프레시 토큰은 로그인 시점부터 14일이에요.
- 같은 시각에 발급해도 JWT의 jti가 달라 서로 다른 토큰이 돼요.
- 가입·메일은 아직 보류 상태예요. 로그인 API가 계정을 새로 만들지 않아요. 자동 테스트는 별도 계정을 준비해요.

## POST /auth/refresh

```json
{"refreshToken":"로그인 응답의 리프레시 토큰"}
```

200 응답은 `{"accessToken":"...","refreshToken":"..."}`이에요. FE는 두 값을 함께 교체해야 해요.

- 리프레시 토큰은 SecureRandom 32바이트의 URL-safe 문자열이에요. JWT와 별도 형식이에요.
- DB에는 원문 대신 sha256: 접두사를 붙인 SHA-256 해시만 저장해요. 기존 token VARCHAR(512) 컬럼을 그대로 사용해요.
- 행 잠금을 건 트랜잭션에서 기존 해시를 새 해시로 교체해요. 동시에 같은 토큰을 재발급해도 한 요청만 성공하고, 이전 토큰은 다시 사용할 수 없어요.
- 회전해도 로그인 때 정한 14일 만료 시각을 연장하지 않아요. 만료되면 다시 로그인해요.
- 없는 값·변조·만료·폐기·이미 교체된 토큰, 액세스 JWT를 보내면 401 UNAUTHORIZED예요.
- FE는 동시 refresh를 한 요청으로 모으고 최신 토큰을 저장한 뒤 원래 요청을 다시 보내요. 요청/응답 필드 변경은 없으며 이 호출 정책은 FE 확인이 필요해요.
- 기존 공통 필터는 공개 API라도 잘못된 Authorization을 받으면 401이에요. login/refresh 요청에는 만료된 액세스 JWT를 붙이지 않아요.
- 수동 저장한 원문 토큰이나 가짜 서버의 mock-refresh-token은 실제 재발급에 쓰지 않아요. 실제 로그인으로 새 토큰을 받아요.

회전과 이전 값 무효화는 [RFC 9700의 refresh token 보호 안내](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)를 참고했어요. 이번 범위는 이전 값 거부까지이며, 재사용 탐지 후 전체 토큰 가족을 폐기하는 별도 추적 구조는 포함하지 않아요.

## 개발용 로그인

/auth/dev-login은 기본으로 비활성화했어요. 필요한 개발 환경에서만 아래 두 조건을 함께 설정해요.

- local 프로필
- DEV_LOGIN_ENABLED=true (또는 app.auth.dev-login.enabled=true)

prod가 함께 켜져 있으면 허용 설정이 있어도 컨트롤러를 만들지 않아요. 이 도우미는 기존처럼 액세스 JWT만 반환하며 실제 비밀번호 로그인·리프레시 검증은 위 API를 사용해요.

## 저장·로그

- 새 컬럼·테이블과 운영 DB 설정 변경은 없어요. 기존 수동 SQL / prod validate 방식을 유지해요.
- 비밀번호·토큰 DTO의 toString을 가리고 입력 검증 로그에는 예외 타입만 남겨요.
- 비밀번호 재설정·로그아웃 API는 이번 구현 범위가 아니에요. DB의 해당 회원 refresh 행을 폐기하면 재발급은 거부돼요.

## 검증

AuthApiTest는 로그인, 해시 저장, 재발급·재사용, 만료 경계, 변조, 폐기, 동시 재발급, BE2 카드·계획 API 연동과 검증 로그를 확인해요. JwtProviderTest / DevLoginConfigurationTest는 JWT와 local/prod 경계를 확인해요.

AuthLiveMySqlTest는 별도 임시 MySQL의 실제 HTTP와 동시 재발급을 검사해요. localhost의 reeltrip_issue12 일회용 DB가 있을 때만 실행돼요.

```powershell
# 임시 MySQL을 띄우고 schema.sql을 수동 적용한 뒤 선택해서 실행해요.
$env:REELTRIP_AUTH_MYSQL_URL='jdbc:mysql://127.0.0.1:포트/reeltrip_issue12?serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false'
.\gradlew.bat test verifyMockIsolation
```

이 선택 테스트는 로컬 일회용 root / 빈 비밀번호 DB용이에요. 운영 주소·계정을 사용하지 않아요. 환경 변수가 없으면 해당 실제 MySQL 테스트는 건너뛰고 H2 계약 검증은 계속 실행해요.
