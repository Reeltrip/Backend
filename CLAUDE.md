@AGENTS.md
@docs/decisions.md

# Wayvi BE — Claude Code 추가 규칙

이 파일은 레포 루트에 둡니다. 위 두 줄로 `AGENTS.md`(팀 작업 규칙)와 `docs/decisions.md`(그 뒤 확정된 결정)를 함께 읽습니다. 에이전트 팀의 모든 팀원도 이 파일을 읽습니다.

## 문서 우선순위

규칙이 서로 다르면 위에 있는 것을 따릅니다.

1. 사용자가 이번 대화에서 직접 한 말
2. `docs/decisions.md` — 2026-11-12까지 확정된 결정. `AGENTS.md`보다 최신입니다.
3. `docs/api-spec.md` — API별 요청·응답·에러 코드
4. `docs/erd.md`, `docs/schema.sql` — 테이블·컬럼·제약
5. `AGENTS.md` — 팀 작업 규칙(응답 방식, 커밋, 브랜치, 이름 짓기, 계층, 예외, 테스트)

문서끼리 어긋나는 곳을 찾으면 마음대로 고르지 말고 어긋난 곳을 적어 사용자에게 묻습니다.

## 담당 범위

- BE 1 = 반기태(Notion "기태"): 인증·회원, 링크 분석 흐름, 외부 API(`infra/fetcher`, `infra/map`, `infra/mail`, `infra/storage`), 배포, `global/**`
- BE 2 = 김승윤: AI 추출(`infra/ai`), 요약카드(`domain/place`, 검색 컨트롤러 제외), 여행계획(`domain/plan`), 공유
- 이 레포에서 Claude가 맡는 일은 사용자가 따로 말하지 않으면 **BE 2 범위**입니다. BE 1 소유 파일은 읽기만 하고, 고쳐야 하면 무엇을 왜 고칠지 사용자에게 먼저 묻습니다.
- 두 사람이 같이 고치는 파일은 `global/exception/ErrorCode.java` 하나입니다. `// BE 2: 카드·계획` 블록 안에만 추가합니다.
- 자세한 경계와 넘겨주는 타입은 `docs/work-split.md`에 있습니다.

## 작업 습관

- 시작 전에 관련 화면 번호(S0-1 ~ S5-1)와 `docs/api-spec.md`의 해당 절을 확인합니다. 코드 주석·커밋·PR에 화면 번호를 그대로 씁니다.
- 아직 실제 구현이 없는 API를 FE가 써야 하면 정한 형식대로 가짜 응답부터 엽니다: `// TODO: 가짜 응답, 실제 구현으로 교체`.
- API를 만들면 로컬에서 성공·실패 응답을 모두 직접 호출해 확인합니다. 확인 못 했으면 못 했다고 씁니다.
- `./gradlew test`가 통과하지 않으면 끝난 것이 아닙니다.
- `build.gradle` 의존성 추가, DB 데이터 삭제, GCP 설정 변경, 푸시·PR 생성은 하기 전에 사용자에게 묻습니다.
- 비밀 값(API 키, DB 비밀번호, JWT 비밀 키)은 코드·로그·답변에 쓰지 않습니다. 환경 변수로만 받습니다.

## 쓸 수 있는 도구

- ERD: ERDCloud `https://www.erdcloud.com/d/QBnezeqv2SukF6Fnk` — `erdcloud` MCP가 연결돼 있으면 테이블 확인에 씁니다. 테이블을 바꾸면 ERDCloud와 `docs/erd.md`, `docs/schema.sql`도 같이 고칩니다(사용자 확인 후).
- Wayvi Notion은 게스트 권한이라 Notion MCP로 읽을 수 없습니다. Notion API 명세를 고쳐야 하면 바꿀 내용을 정리해 사용자에게 넘깁니다.
