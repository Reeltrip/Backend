# 네이버·일반 웹 본문 수집 (#19)

S1-2 / S1-3의 수집 단계에서 네이버 블로그와 일반 웹의 정적 본문을 가져와요. #18의 ContentFetcher에 NAVER_BLOG / ETC 구현을 등록하며 기존 FetchedContent / LlmClient / PlaceDraft 계약을 유지해요. 새 API·DB·환경 변수는 없어요.

사용자가 HTML 파서·HTTP 클라이언트 의존성 추가를 보류해서 build.gradle을 바꾸지 않았어요. JDK 21의 java.desktop에 있는 ParserDelegator와 java.net 소켓/TLS를 사용해요. GUI를 띄우거나 JavaScript·외부 리소스를 실행하지 않아요. 모든 HTML5 구조를 브라우저처럼 복구하는 파서는 아니에요.

## 네이버 블로그

- blog.naver.com / m.blog.naver.com의 `/{blogId}/{logNo}` 또는 `/PostView.naver?blogId=...&logNo=...`를 지원해요.
- blogId는 영문·숫자·밑줄·하이픈 1~64자, logNo는 숫자 1~20자예요. 중복 식별자·인코딩한 경로 주입·글 없는 블로그 홈은 거부해요.
- 검증한 식별자로 HTTPS PostView 주소를 만들어요. 원본의 추적 쿼리는 넘기지 않아요.
- 신 편집기의 se-main-container 또는 구 편집기의 postViewArea 본문을 읽어요. 공지·로그인·오류 화면의 문구를 글 본문으로 대신 쓰지 않아요.
- 본문이 없고 mainFrame이 있으면 같은 글의 네이버 PostView 주소인지 확인한 뒤 한 번만 따라가요. 다른 글·호스트·내부 주소의 프레임은 요청하지 않아요. 프레임 재귀는 지원하지 않아요.

## 일반 웹 본문

- article → main/role=main → 알려진 content 영역 → body 순서로 후보를 골라요. 같은 우선순위에서는 텍스트가 가장 긴 후보를 사용해요.
- 메뉴·헤더·푸터·사이드바·댓글·폼·스크립트·CSS·숨겨진 영역을 제외하고 텍스트를 반환해요. HTML 태그를 정규식으로 통째로 지우는 방식은 사용하지 않아요.
- JDK 파서가 body의 style 태그를 무시하는 경우에 대비해 따옴표·주석을 구분하는 선형 스캔으로 script/style 원문을 먼저 제외해요.
- 텍스트는 공백을 정리하고 최대 30000자까지 전달해요. 마지막 서로게이트 쌍이 잘리지 않게 해요. 노드 20000개·중첩 깊이 128을 넘으면 실패해요.
- BOM → HTTP charset → HTML meta charset → UTF-8 순서로 인코딩을 결정해요. UTF-8·EUC-KR와 BOM의 UTF-16 등을 처리하고, 잘못된 바이트·미지원 문자셋은 실패해요.
- og:image, 없으면 twitter:image 후보를 읽어요. HTML base 태그 대신 최종 페이지 주소를 기준으로 상대 경로를 해석해요. HTTPS·공개 DNS를 확인한 후보만 전달하고, 썸네일 검증 실패는 본문 성공에 영향을 주지 않아요. 이미지를 내려받지는 않으며 이후 복사 단계에서 다시 검사해야 해요.
- 비어 있는 본문, 로그인·차단·JavaScript 렌더링만 필요한 페이지는 지원하지 않아요. 이런 페이지의 동작을 브라우저로 우회하지 않아요.

## 외부 접속·시간·크기 제한

- 입력은 HTTP(S), 2048자 이하, userinfo 없음, 기본 포트(80/443)만 허용해요. localhost / .local / .internal 등 내부 호스트 표기를 차단해요.
- 각 요청·리디렉션의 모든 DNS 응답을 검사해요. 하나라도 내부·루프백·링크 로컬·메타데이터·예약/문서용 주소면 전체를 거부해요. IPv4/IPv6 전환 대역도 제한해요.
- 검사한 첫 공개 IP를 InetSocketAddress에 직접 넣어 연결해요. 연결 시 호스트를 다시 DNS 조회하지 않으므로 검사 뒤 주소가 바뀌어도 다른 IP로 접속하지 않아요. HTTPS는 원래 호스트로 SNI와 JDK 인증서·호스트 검증을 유지해요.
- 연결 재사용·프록시·쿠키·인증 헤더·자동 재시도는 없어요. 첫 IP 연결이 실패해도 다른 IP로 재시도하지 않아요.
- HTTP/1.0·1.1의 GET 응답을 지원해요. 고정 길이·chunked·연결 종료 본문과 gzip을 처리하고, 모호한 Content-Length/Transfer-Encoding·중복 제어 헤더·다른 압축 형식은 거부해요.
- 본문은 수신·압축 해제 각각 최대 1MiB, 헤더는 32KiB/100개, 한 줄은 8KiB, 청크는 16384개까지예요.
- DNS 2초, 연결 2초, 읽기 대기 3초, 프레임·리디렉션을 포함한 한 링크의 네트워크 기한은 총 10초예요. 별도 타이머가 TLS 핸드셰이크·본문 수신 중에도 기한에 소켓을 닫아요. 파싱이 끝난 뒤에도 기한을 검사해요.
- DNS 작업은 대기열 없이 최대 4개로 제한해요. 시간이 지난 DNS 작업은 취소하고, 모두 사용 중이면 링크 실패로 처리해요.
- 301/302/303/307/308은 최대 3번 따라가며 목적지를 다시 검사해요. HTTPS→HTTP 이동과 루프는 거부해요. meta refresh·JavaScript·임의 iframe은 따라가지 않아요.
- 실패는 원문·본문·인증정보를 넣지 않은 기존 SOURCE_FETCH_FAILED로 반환해요. 공유 상태나 다른 링크를 변경하지 않아요. 링크별 상태 반영과 여러 링크의 실행·집계는 #23 범위예요.

## 검증

저장한 네이버 신/구 편집기·mainFrame·일반 기사·빈 HTML로 본문과 실패를 확인해요. 고정 DNS/HTTP 응답과 로컬 소켓으로 공개 IP 고정, 혼합 DNS·내부 주소·리디렉션 차단, 프레이밍·gzip 크기·본문 완료 기한을 확인해요. 기존 Gemini 고정 응답으로 텍스트 입력과 원본 sourceUrl 보존, 영상 시점 미생성을 확인해요. 자동 테스트는 실제 사이트·Gemini를 호출하지 않아요.

선행 #18 브랜치 기준 전체 테스트 338개(이번 수집 테스트 81개 포함)와 verifyMockIsolation이 통과했어요. 실패·오류·건너뜀은 0개예요. 기존 선택 MySQL 테스트도 일회용 로컬 DB에서 실행했어요. 첫 검증에서 CSS 제외와 파서 한도 초과 동작의 실패를 수정했으며, 한도 초과 시 Reader를 중단하고 부분 본문을 반환하지 않는 것을 확인했어요.

### 로컬 공개 링크 확인 (2026-11-14, Java 21 / Windows)

본문 원문은 저장하거나 출력하지 않고 압축 해제 뒤 HTML 바이트·추출 길이만 확인했어요.

| 공개 페이지 | HTML 바이트(압축 해제 후) | 추출 문자 수 | 결과 |
| --- | ---: | ---: | --- |
| [example.com](https://example.com/) | 577 | 156 | HTTPS와 정적 본문 수집 성공 |
| [교토 관광 공식 니시키 시장 페이지](https://kyoto.travel/ko/destinations/kyoto-nishiki-food-market/) | 152283 | 354 | HTTPS와 정적 본문 수집 성공, 썸네일 후보 있음 |
| [네이버 공개 교토 식당 글](https://blog.naver.com/6_bbang/223175797866) | 297145 | 2727 | PostView 변환·본문 수집 성공, 썸네일 후보 있음 |

이 결과는 해당 날짜·페이지·로컬 네트워크에 한정돼요. 사이트 구조·공개 상태·차단 정책이 달라지면 실패할 수 있어요.

공개 TLS 오류 확인에서 wrong.host.badssl.com은 SSLHandshakeException으로 거부됐어요. self-signed.badssl.com은 읽기 시간 초과여서 자체 서명 인증서 거부 동작은 그 호출로 확인하지 못했어요.

### 남은 확인

- [ ] Cloud Run에서 공개 링크와 비공개·삭제 글의 실제 응답 확인은 남아 있어요. 운영 URL은 확인됐지만 2026-11-16 사용자 요청으로 추가 테스트·운영 실호출을 생략해요.
- [x] #23의 접수 커밋 → 수집 → LLM → 초안 저장·링크별 상태 연결은 main에 반영됐어요.
- [x] 기존 #19 구현은 PR #53으로 main에 반영됐어요. 후속 보완은 별도 feature 브랜치에서 main 대상으로 올려요.

### 2026-11-16 네이버 단축 링크 보완

`https://naver.me/{code}`를 NAVER_BLOG로 분류해요. 단축 링크의 추적 query를 전달하지 않고 HTTPS로 해석하며 각 redirect는 DNS 조회·접속 전에 naver.me 또는 유효한 blog.naver.com / m.blog.naver.com 글인지 검사해요. 처음 확인된 블로그 글과 다른 글로 다시 이동하면 실패해요. 기존 내부 주소 차단·검증 IP 고정·최대3 redirect·10초 전체 제한을 그대로 사용해요.

네이버 지도·카페 등 블로그 외 대상으로 가는 단축 링크, 로그인·JS 이동만 있는 페이지는 SOURCE_FETCH_FAILED예요. 최종 본문은 블로그 파서를 사용하고 결과의 sourceUrl은 입력한 단축 원문으로 유지해요. 기존 긴 글 주소 동작·API·DB·새 의존성 변경은 없어요. 추가 테스트 실행과 실제 단축 링크 호출은 사용자 요청으로 생략했어요.

분석 실패 로그에는 sourceLinkId·오류 코드·예외 종류만 남겨요. URL·본문·API 키·예외 메시지와 전체 스택은 남기지 않아요. 운영 실링크 검증을 다시 진행할 때 해당 ID의 DB 상태와 함께 확인할 수 있어요.

참고: [JDK HTML 파서](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/text/html/parser/ParserDelegator.html), [IP 주소 소켓 연결](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html), [TLS 호스트 검증·SNI](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html).
