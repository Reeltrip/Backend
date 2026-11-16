# 시연용 관리자 화면 (#76)

2026-11-16 사용자 요청: Spring Boot 안에서 관리자 화면과 API를 제공하고, 초기화는 지정된 데모 계정으로만 한정해요. 테스트 실행은 생략하고 feature 브랜치에서 main 대상 PR을 만들어요. 운영 설정 변경·배포·실제 데이터 삭제는 이번 작업에서 실행하지 않아요.

## 사용 준비

회원가입으로 운영자 계정과 별도의 데모 계정을 준비해요. 기존 로그인 응답의 userId 또는 GET /users/me로 본인의 ID를 확인해요. 비밀번호·토큰을 코드·문서·채팅에 적지 않아요.

| 환경 변수 | 값 | 기본값 |
| --- | --- | --- |
| ADMIN_USER_IDS | 관리자 회원 ID를 쉼표로 구분 | 빈 목록: 모든 관리자 API 차단 |
| DEMO_USER_IDS | 초기화·샘플 생성을 허용할 회원 ID를 쉼표로 구분 | 빈 목록: 모든 초기화·샘플 생성 차단 |

ID는 양의 정수예요. 잘못된 항목·빈 중간 항목·범위를 넘는 숫자는 시작 단계에서 거부해요. 관리자 계정도 데모 목록에 별도로 지정돼야 초기화할 수 있어요. 예시 ID를 실제 사용자 ID로 착각해 등록하지 않아요.

새 런타임 환경 값은 자동 배포가 등록하지 않아요. 운영 담당자가 기존 Cloud Run 환경을 보존하면서 위 두 값만 별도로 추가한 뒤 사용해요. 미설정 상태로 병합해도 관리 데이터는 열리지 않아요. DB 테이블·컬럼·인덱스·의존성·Secret은 추가하지 않아요.

서버 주소의 `/admin/`를 열고 관리자 계정으로 로그인해요. HTML·CSS·JS·시연용 SVG는 공개 정적 파일이며 데이터는 포함하지 않아요. 관리자 화면도 기존 30분 access JWT를 사용해요. 토큰은 탭 메모리에만 두고 localStorage·sessionStorage·URL에 저장하지 않아요. 새로고침·탭 종료·화면 로그아웃 시 지우며 만료되면 다시 로그인해요. 별도 관리자 비밀번호나 브라우저에 넣는 관리자 키는 없어요. 화면 로그아웃은 현재 탭의 토큰 제거이며 기존 JWT 자체를 즉시 폐기하는 동작은 아니에요.

## 화면과 변경 범위

- 계정 검색: 이메일·닉네임 부분 검색, 회원 ID 일치 검색. 최대 50개씩 페이지로 읽고 비밀번호·토큰·동의 정보는 반환하지 않아요.
- 링크 분석: 계정별 원문 링크, 링크 상태·실패 코드/이유·최근 변경. 초안 원문이나 인증정보는 목록에 넣지 않아요. 관리자 조회가 분석을 재실행하거나 강제로 완료하지 않아요.
- 카드: 보관함 목록, 이름·카테고리·주소·위도/경도·도시·사진 URL·가격·핵심 포인트·교통/팁 항목·메모 편집, 휴지통 이동, 복원.
- 여행계획: 선택 계정이 소유한 계획만 목록·일자별 상세로 읽고 삭제해요. 다른 계정의 공유 계획을 임의로 선택 계정 멤버에 추가하지 않아요.
- 시연 준비: 데모 계정에만 표시해요. 빈 계정에 시연 카드 2장과 다음 날의 하루 계획·노드 2개를 만들어요. 이름·주소·좌표·그림은 직접 작성한 예시이며 실제 장소 정보가 아니에요. Google·LLM·메일·Storage API를 호출하지 않아요.

카드 변경·휴지통 이동·복원·계획 삭제·샘플 생성은 기존 도메인 서비스의 검증을 재사용해요. 관리자는 일반 계정 데이터도 편집할 수 있어요. 카드 휴지통 이동은 기존 규칙대로 해당 카드를 쓰는 계획의 노드·플랜B·숙소 선택을 제거하며, 복원은 보관함에만 돌아와요. 계획 삭제는 일자·노드·숙소·멤버 관계를 같이 삭제해 복원되지 않아요. 화면에서 대상을 확인한 뒤 실행해요.

## 초기화

1. 데모 계정을 선택하고 **초기화 대상 미리보기**를 눌러 정확한 이메일·회원 ID·테이블별 건수를 확인해요. 휴지통 카드도 포함돼요.
2. 진행 중 분석, 공유가 켜진 소유 계획, 다른 회원이 참여한 소유 계획, 다른 계정 계획에서 사용하는 내 카드, 다른 계정 카드가 사용하는 내 분석 링크가 있으면 실행을 차단해요. 연결 관계를 운영자가 검토하고 먼저 정리해요.
3. `초기화 <회원 ID>`를 직접 입력하고 최종 확인을 거쳐요. 서버에서도 데모 허용 목록·관리자 권한·확인 문구를 다시 검사해요. 브라우저 버튼 표시로 권한을 판단하지 않아요.
4. 미리보기에서 계산한 계정 ID와 테이블별 정렬된 ID 목록의 SHA-256을 서버가 잠금 후 다시 비교해요. ID 목록이 달라지면 409이며 미리보기를 새로 읽어요. 이 값은 인증 토큰이 아니며, 내용 변경 전체를 감지하는 버전 번호도 아니에요.
5. 회원→분석 요청/링크→계획→카드→자식 행을 잠그고 FK 역순으로 확정 ID만 삭제해요. READ_COMMITTED 트랜잭션·20초 제한, 삭제 건수 확인과 FK 제약을 사용해 실패하면 전체 롤백해요. 테이블당 10,000행을 넘는 계정은 이 도구로 처리하지 않아요. 시연 계정의 다른 작업을 멈추고 진행해요.

삭제 대상: source/source_link, 휴지통 포함 place/place_key_point, 소유 plan 및 plan_day/plan_day_area/plan_node/plan_alternative/plan_stay/plan_member.

유지 대상: user, email_code, refresh_token, password_reset_token, Storage 파일, 다른 계정이 소유한 데이터·계획과 그 멤버 관계. 초기화는 회원 탈퇴·토큰 폐기·버킷 청소를 하지 않아요. 초기화 후 샘플 만들기는 별도 버튼이며 두 작업은 각각 원자적이에요. 샘플은 관련 데이터가 전부 비어 있을 때만 생성해 중복 생성을 막아요.

## 관리자 API

모든 `/admin/api/**`는 유효 JWT·ADMIN_USER_IDS·실존 회원을 확인하며 응답은 no-store예요. 미인증401, 관리자 아님403, 없는 대상404, 입력 형식 오류400이에요. 기존 FE API 요청·응답은 변경하지 않아요. 아래 API는 이 관리자 화면 전용 추가 계약이에요.

| 메서드·주소 | 요청·응답 |
| --- | --- |
| GET /admin/api/me | userId/email/nickname/demo |
| GET /admin/api/users | query(0~100자), page(0~100000), size(1~50, 기본20); users/page/size/hasNext |
| GET /admin/api/users/{userId}/sources | page/size; links/page/size/hasNext. 링크 단위 최신순 |
| GET /admin/api/users/{userId}/places | page/size, trash(기본false); places/page/size/hasNext |
| GET /admin/api/users/{userId}/places/{placeId} | 기존 PlaceResponse |
| PATCH /admin/api/users/{userId}/places/{placeId} | 기존 카드 부분 수정 JSON → PlaceResponse |
| DELETE /admin/api/users/{userId}/places/{placeId} | 휴지통 이동, 204 |
| POST /admin/api/users/{userId}/places/{placeId}/restorations | 보관함 복원, 204 |
| GET /admin/api/users/{userId}/plans | page/size; plans/page/size/hasNext. 소유 계획만 |
| GET /admin/api/users/{userId}/plans/{planId} | 기존 PlanDetailResponse |
| DELETE /admin/api/users/{userId}/plans/{planId} | 소유 계획 삭제, 204 |
| GET /admin/api/users/{userId}/reset-preview | userId/counts/allowed/blockers/fingerprint/confirmation |
| POST /admin/api/users/{userId}/resets | {fingerprint,confirmation} → 삭제 테이블별 건수 |
| POST /admin/api/users/{userId}/samples | {imageUrl}: HTTPS 이미지 주소(로컬만 localhost/127.0.0.1 HTTP 허용) → planId/places |

샘플 버튼은 이 서버의 `/admin/sample-place.svg`를 사용해요. 현재 origin에서 만든 예시 그림 주소이며 외부 사진을 가져오지 않아요. 링크를 열 때는 http(s)만 허용하며 새 창에 인증 헤더를 전달하지 않아요. 화면에 저장된 문자열은 textContent로 넣어요. 관리자 경로에는 CSP·frame 차단·no-referrer·no-store를 적용하고 쿠키 인증은 사용하지 않아요. 변경 성공은 커밋 뒤 운영자 ID·동작·대상 회원 ID·리소스 ID만 로그에 남겨요.

추가 오류: ADMIN_FORBIDDEN(403), ADMIN_DEMO_ONLY(403), ADMIN_OPERATION_BLOCKED(409), ADMIN_PREVIEW_CHANGED(409), ADMIN_DEMO_NOT_EMPTY(409). 잠금 충돌·무결성 충돌·시간 초과도 초기화/관리 API에서는 409로 응답하고 원본 SQL이나 값을 응답하지 않아요.

## 확인 범위

사용자 요청에 따라 테스트 작성·실행, 로컬 API 실호출, 브라우저 동작 검증, 운영 DB 초기화와 운영 배포를 수행하지 않아요. Java 21 운영 JAR 빌드·JavaScript 문법·변경 내용 정적 검사를 수행해요. 기존 저장소 PR CI 설정은 유지하며 자동으로 실행될 수 있어요. 다른 BE 리뷰 후 병합하고 실제 관리자·데모 ID 설정과 운영 사용 확인은 운영 담당자가 진행해야 해요.
