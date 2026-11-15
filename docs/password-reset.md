# #14 비밀번호 재설정 (S0-1a)

2026-11-16 최신 main의 #64 SMTP·가입과 기존 인증 테이블을 재사용해요. 새 의존성·테이블·컬럼·운영 Secret은 없어요.

## API와 메일

- `POST /auth/password-reset`에 email을 보내면 빈 200이에요. 형식 오류는 400 INVALID_INPUT이에요. 가입 여부·60초 내 재요청·SMTP 실패·작업 큐 포화는 같은 200으로 처리해요. 같은 별도 작업 큐에 먼저 접수해 계정 조회·SMTP 대기 시간이 응답에 드러나지 않게 해요.
- 메일 작업은 인스턴스당 스레드 2개·대기 32개로 제한해 분석 풀과 분리해요. 상태·재발송 제한은 DB에 둬요. 프로세스 재시작으로 아직 처리하지 못한 작업은 유실될 수 있으므로 메일이 오지 않으면 잠시 뒤 다시 요청해요.
- 토큰은 무작위 32바이트의 URL-safe 문자열(43자)이고 DB에는 SHA-256 64자만 있어요. 발급 후 30분, 60초 뒤 재발급하면 이전 링크를 무효로 해요. SMTP 실패 시 새 토큰 저장·이전 링크 삭제를 함께 되돌려요.
- 링크는 기존 명세의 `${SHARE_BASE_URL}/password-reset?token=...`예요. 기존 MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM과 5초 SMTP 접속·읽기·쓰기 제한을 재사용해요. **prod의 SHARE_BASE_URL은 실제 공개 HTTPS FE 주소여야 해요.** 미설정 localhost·HTTP·userinfo·query·fragment가 있는 설정이면 재설정 메일을 보내지 않아요. Cloud Run 설정을 이 PR에서 바꾸지 않아요.
- FE는 링크의 token을 꺼내 **본문**으로 `POST /auth/password-reset/verify`에 보내요. maskedEmail / expiresAt을 받고 확인만으로 소비하지 않아요. 잘못된·교체된·사용한 토큰은 INVALID_RESET_TOKEN, 30분 경계부터 RESET_TOKEN_EXPIRED예요.
- `POST /auth/password-reset/confirm`에 token / newPassword를 보내면 성공 204예요. 가입과 같은 영문·숫자 포함 8자 이상, UTF-8 최대 72바이트 규칙을 써요. 비밀번호 확인은 FE에서 비교해요. 자동 로그인하지 않아요.

요청·성공 응답은 no-store예요. 원문 토큰·비밀번호·받는 주소를 로그에 넣지 않아요. SMTP 예외 메시지와 DTO toString에도 숨겨요. FE 재설정 화면에는 외부 분석 도구를 넣지 말고 Referrer-Policy: no-referrer를 적용하고 토큰을 읽은 뒤 주소 기록을 지우는 방식을 검토해요. 화면 주소와 성공·만료 안내의 FE 연동은 아직 확인이 필요해요.

## 동시 요청과 세션

회원→토큰 순서로 잠가 비밀번호 BCrypt 변경·토큰 1회 소비·그 회원의 모든 refresh 삭제를 원자적으로 처리해요. 로그인도 회원 잠금 안에서 검사·발급하고, refresh도 회원→refresh 순서로 잠가요. 이전 비밀번호로 검사한 로그인이 reset 뒤 refresh를 발급하거나, 회전된 refresh가 삭제에서 빠지는 일을 막아요.

MySQL REPEATABLE READ에서 이전 조회 스냅샷이 있어도 새로 발급된 refresh를 지우도록 bulk DELETE를 사용해요. 다른 회원 refresh는 유지해요. 이미 발급된 access JWT는 기존 최대 30분 만료까지 유효할 수 있어요. 이번 계약은 refresh 폐기이며 access 즉시 차단을 위한 DB·JWT 버전 변경은 없어요.

## 검증과 배포 뒤 확인

- PasswordResetApiTest: 비동기 접수200, verify 반복·미소비, confirm204·재사용 거부, 만료·60초 경계, 교체·SMTP rollback, 이전 비밀번호·본인 refresh 폐기·다른 회원 유지, 비밀번호 규칙·로그 숨김.
- PasswordResetSmtpTest / PasswordResetDispatcherTest: 실제 SMTP 대신 고정 메일 송신기로 링크·발신 이름·예외 숨김·신뢰한 FE 주소·별도 큐·포화를 검증해요.
- PasswordResetMySqlTest: 실제 HTTP 흐름, 동시 소비 1회, 동시 발송 1회, 이전 스냅샷 뒤 로그인에서 발급된 refresh까지 폐기되는지 확인해요. 루프백의 일회용 reeltrip_issue14 DB에서만 실행해요.
- PR CI에 일회용 MySQL 8 서비스를 추가해 모든 기존 선택 MySQL 테스트와 수동 schema.sql을 확인해요. 기본 H2와 Docker 검증도 유지해요. Windows 재현은 scripts/test-mysql.ps1을 사용할 수 있어요.

다른 BE 리뷰·병합 후 기존 자동 배포가 끝나면 실제 FE 주소·본인 테스트 이메일로 요청200→메일 수신→verify200→confirm204→이전 refresh401→새 비밀번호 로그인200을 확인해요. 이번 작업에서는 실제 수신자에게 메일을 보내거나 운영 비밀번호·데이터를 바꾸지 않았어요.

공식 보안 근거: [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), 2026-11-16 확인. 기존 요청·응답은 [api-spec.md](api-spec.md), 재현은 [password-reset.http](password-reset.http)에 있어요.
