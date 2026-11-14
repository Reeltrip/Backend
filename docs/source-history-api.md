# 최근 링크 수집 내역 (#17)

S1 링크 홈의 GET /sources를 구현했어요. 인증 토큰의 회원에 속한 source만 읽고, 저장된 제목과 상태를 반환해요. 링크 수집·AI 호출·상태 변경·카드 저장은 하지 않아요.

```http
GET /sources?page=0&size=10
Authorization: Bearer {accessToken}
```

```json
{
  "sources": [
    { "sourceId": 12, "title": null, "status": "PENDING", "createdAt": "2026-11-14T10:24:00+09:00" },
    { "sourceId": 11, "title": "오사카 여행", "status": "DONE", "createdAt": "2026-11-13T14:00:00+09:00" }
  ]
}
```

## 페이지·정렬 규칙

- page는 0부터 시작하며 생략하면 0이에요. size는 기본 10, 허용 범위는 1~100이에요. 빈 쿼리 값도 Spring의 기본값을 써요.
- createdAt 내림차순으로 정렬하고 같은 시각이면 sourceId 내림차순이에요. DB에서 회원 필터·정렬·페이지 제한을 함께 적용해요.
- 제목은 분석 전 null을 유지해요. PENDING / ANALYZING / DONE / FAILED를 모두 조회해요.
- 내역이 없거나 마지막 페이지를 넘으면 200과 `{ "sources": [] }`를 반환해요. totalCount 등 추가 필드는 없어요.
- 음수 page, size 0·음수·100 초과, 정수가 아닌 값과 int 범위를 넘은 숫자는 400 INVALID_INPUT이에요.
- JPA 시작 위치의 범위에 맞춰 `page * size <= 2147483647`만 허용해요. 곱셈을 long으로 계산해 오버플로로 500이 되지 않게 해요.
- 요청 사이 새 수집이 추가되면 offset 페이지의 위치가 바뀔 수 있어요. FE에서 새로고침할 때 page=0부터 다시 읽어요.
- 생성 시각은 공통 규칙과 같이 Asia/Seoul의 +09:00 ISO 8601, 초 단위예요. 정렬은 DB에 저장된 원래 시각을 사용해요.

## 인증·응답

- 토큰이 없거나 토큰 회원이 DB에 없으면 401 UNAUTHORIZED예요.
- userId 쿼리로 다른 회원을 선택할 수 없어요. 응답에는 링크 원문·초안·다른 회원 정보가 없어요.
- 성공 응답에 Cache-Control: no-store를 넣어요.
- 인증 토큰을 준비한 뒤 [source-history-api.http](source-history-api.http)로 호출할 수 있어요. 실제 로그인은 별도 #12 PR, 접수·상세는 별도 #16 PR에서 제공해요.

## 수동 SQL

기존 Source 엔티티에는 idx_source_user_created(user_id, created_at)가 선언돼 있지만 docs/schema.sql에는 빠져 있어 추가했어요. 새 DB는 갱신된 schema.sql을 적용해요.

기존 DB는 먼저 `SHOW INDEX FROM source;`로 같은 인덱스가 있는지 확인하고, 없을 때만 [002-source-history-index.sql](sql/002-source-history-index.sql)을 한 번 적용해요. 테이블·컬럼·데이터는 변경하지 않으며 Flyway를 추가하지 않아요. 운영 DB에 자동 적용하지 않아요.

## 검증

SourceHistoryApiTest는 본인 필터, 네 상태·nullable 제목, 서울 시각, 동률 정렬과 여러 페이지, 기본·최대 size, 빈 목록·범위 밖 페이지, 입력 오류·오버플로, 인증 실패와 AI 미호출을 확인해요.

SourceHistoryHttpMySqlTest는 일회용 로컬 MySQL 8.0에서 실제 HTTP로 페이지·동률 정렬·권한·입력 오류와 수동 인덱스를 확인해요. 다음 환경 변수가 없으면 이 MySQL 테스트만 건너뛰어요. 테스트는 localhost의 지정 DB·일회용 root·빈 비밀번호만 사용해요.

```powershell
# 일회용 DB에 갱신된 schema.sql 또는 기존 스키마 + 002 인덱스 SQL을 적용해요.
$env:REELTRIP_HISTORY_MYSQL_URL='jdbc:mysql://127.0.0.1:포트/reeltrip_issue17?serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false'
.\gradlew.bat test verifyMockIsolation
```

새 의존성·운영 환경 변수는 없어요. #17 브랜치는 main에서 분리했으며 #16의 접수·상세 API와 URL이 겹치지 않아요. #16을 합치면 생성한 수집을 이 목록에서 확인할 수 있어요. 실제 분석 실행은 #23에서 연결해요.
