# 인증 DB 적용과 서비스 연결 (#9)

2026-11-14 사용자 결정: 기존 SQL 파일로 수동 적용하고 운영 `ddl-auto=validate`를 유지해요. Flyway와 새 라이브러리는 추가하지 않아요. 이 작업에서 GCP나 운영 DB를 변경하지 않아요.

## 저장하는 것

| 테이블 | 값·규칙 |
| --- | --- |
| email_code | 숫자 6자리, 발송 후 5분, 오입력 횟수·확인 여부·발송 시각 |
| password_reset_token | 원문 대신 SHA-256 해시, 30분, used_at으로 1회 사용 |
| refresh_token | 회원별 토큰·만료 시각, 토큰 유니크, 회원별 일괄 폐기 |
| user | 기존 이메일 유니크와 엔티티를 유지 |

인증 테이블에는 created_at만 있으므로 updated_at이 있는 BaseTimeEntity를 상속하지 않아요. 인증 시각은 서비스가 주입받은 Clock으로 계산해 팩토리·상태 변경 메서드에 넘겨요.

## 비어 있는 MySQL

1. 대상 DB가 비어 있는지, 접속 대상이 맞는지 먼저 확인해요.
2. [schema.sql](schema.sql)을 수동 적용해요. 이 파일은 15개 테이블의 초기 생성용으로, 기존 DB에 통째로 다시 적용하지 않아요.
3. 앱을 prod의 validate로 시작해 컬럼과 타입이 맞는지 확인해요.
4. SHOW INDEX로 아래 제약을 별도로 확인해요. Hibernate validate는 유니크·인덱스 존재까지 검증하지 않아요.

| 테이블 | 확인할 제약 |
| --- | --- |
| user | email 유니크 |
| email_code | idx_email_code_email_created(email, created_at) |
| password_reset_token | token 유니크, user_id FK |
| refresh_token | token 유니크, user_id FK |

기존 schema.sql은 일부 제약이 주석에만 있었어요. 이번에 user.email, 두 token의 유니크와 email_code 복합 인덱스를 실제 DDL로 반영했어요.

## 이미 테이블이 있는 MySQL

1. 백업·현재 테이블/인덱스와 배포 대상을 확인해요.
2. user 테이블이 있고 인증 3개 테이블만 없다면 [sql/001-auth-tables.sql](sql/001-auth-tables.sql)을 적용해요. 기존 데이터를 지우거나 user를 다시 만들지 않아요.
3. 이미 인증 테이블이 있으면 SHOW CREATE TABLE과 엔티티를 먼저 대조해요. IF NOT EXISTS는 기존 컬럼·제약을 고치지 않아요.
4. user.email 유니크가 이미 있으면 유지해요. 없다면 중복 이메일을 조회하고, 데이터 처리 방식을 확인한 뒤 유니크를 추가해요. 임의로 중복 행을 삭제하지 않아요.

```sql
SHOW CREATE TABLE user;
SHOW INDEX FROM user;
SELECT email, COUNT(*) FROM user GROUP BY email HAVING COUNT(*) > 1;
-- email 유니크가 없고 중복 데이터가 없음을 확인한 뒤에만 실행해요.
-- ALTER TABLE user ADD CONSTRAINT uk_user_email UNIQUE (email);
```

## 다음 인증 API에서 지킬 것

- 가장 최근 이메일 코드는 created_at과 email_code_id 역순으로 찾아요. 같은 시각 재발송도 마지막 행이 최신이에요.
- 이메일 코드 상태 변경은 findByEmailCodeIdForUpdate, 토큰 소비는 findByTokenHashForUpdate / findByTokenForUpdate를 쓰기 트랜잭션 안에서 호출해요. 새 발급과 동시 재발송 정책은 해당 서비스 이슈에서 완성해요.
- 오입력 횟수는 예외로 트랜잭션이 롤백돼 사라지지 않도록 저장 경계를 나눠요. 이 저장소 작업만으로 인증 API의 동시성 정책이 완성된 것은 아니에요.
- canResend는 발송 후 60초, canSignUp은 인증 완료 후 발송 30분 전까지만 허용해요. 인증번호 5분 만료와 가입 가능 30분은 별도예요.
- PasswordResetToken.create는 원문을 즉시 해시로 바꾸고 원문 필드·getter를 만들지 않아요. 조회는 hashToken 결과를 사용해요.
- 재설정 성공 시 비밀번호 변경·사용 시각 기록·회원 refresh 토큰 폐기는 하나의 서비스 트랜잭션에서 처리해요.
- 토큰·인증번호·비밀번호를 로그에 출력하거나 엔티티를 API 응답으로 반환하지 않아요.

## 검증

`AuthPersistenceTest`는 기존 H2 MySQL 모드에서 재조회 후 상태·만료 경계·해시·유니크·회원별 폐기를 검증해요.

`AuthMySqlSchemaTest`는 별도 임시 MySQL에 SQL을 수동 적용한 뒤 validate와 저장·잠금 조회·인덱스를 검사해요. 안전하게 지정한 localhost의 reeltrip_issue9 DB가 있을 때만 실행돼요. 환경 변수가 없으면 이 MySQL 검증 한 개는 건너뛰어요.

```powershell
# 새 임시 MySQL 8을 먼저 만들고 schema.sql을 적용해요.
$env:REELTRIP_MYSQL_URL='jdbc:mysql://127.0.0.1:포트/reeltrip_issue9?serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false'
.\gradlew.bat test
```

이 선택 검증 DB는 로컬에서만 접근하는 빈 비밀번호의 일회용 root 계정을 사용해요. 운영 DB 주소·계정을 여기에 넣지 않아요. prod 설정은 바뀌지 않아요.
