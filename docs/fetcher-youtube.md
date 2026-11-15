# 플랫폼 판별·유튜브 입력 (#18)

S1-2 / S2-1에서 사용할 수집 경계를 infra/fetcher에 구현했어요. 공유 FetchedContent / LlmClient / PlaceDraft 계약, Gemini 프롬프트·파서는 바꾸지 않았어요. 새 API·DB·의존성·환경 변수는 없어요.

## 호출 경계

```java
FetchedContent content = sourceContentFetcher.fetch(sourceLink.getUrl());
ExtractionResult result = llmClient.extractCards(content);
```

SourceContentFetcher는 플랫폼을 판별하고 등록된 ContentFetcher로 넘겨요. #18에서는 YoutubeContentFetcher를 등록했고, 후속 #19에서 [네이버·일반 웹 수집기](fetcher-web.md)를 추가해요. 인스타 수집은 #20 범위이며 수집기가 없으면 SOURCE_FETCH_FAILED예요.

유튜브는 원본 url(쿼리·타임스탬프 포함), text=null, 가능한 thumbnailUrl을 반환해요. 자막·설명글·영상 파일을 내려받지 않아요. 기존 GeminiLlmClient가 영상을 입력받고, 카드에 original sourceUrl과 sourceTimestampSec를 채워요. 기존 구현의 쇼츠·단축 URL→watch URL 변환은 그대로 사용해요.

oEmbed의 제목은 내부 Metadata로 읽을 수 있지만 FetchedContent에 새 필드를 추가하거나 제목을 본문으로 쓰지 않아요. source.title 생성은 #23의 도시별 집계 범위예요.

**이 PR만으로 POST /sources의 분석이 실행되지는 않아요.** #23에서 접수 작업과 위 호출 흐름, 저장·상태 집계를 연결해야 해요. #16의 접수 구문 검사는 외부 요청을 하지 않으며, 실제 수집 단계에서는 이 경계로 다시 검사해요.

## 입력 규칙

- HTTP/HTTPS, 최대 2048자, host가 있는 URI만 허용해요. userinfo와 스킴의 기본 포트 외 포트는 거부해요.
- 유튜브 호스트: youtube.com / www.youtube.com / m.youtube.com / music.youtube.com / youtu.be / www.youtu.be예요. 대소문자는 구분하지 않아요. 비슷한 도메인·다른 서브도메인은 ETC예요.
- 지원 영상 경로: `/watch?v={id}`, `/shorts/{id}`, youtu.be의 `/{id}`예요. id는 영문·숫자·밑줄·하이픈 11자이며, 중복 v·빈 v·추가 경로·채널·재생목록·redirect 주소는 거부해요. 11자 형식만으로 공개 영상의 존재를 보장하지는 않아요.
- 인스타 분류: instagram.com / www.instagram.com / m.instagram.com. 네이버 블로그 분류: blog.naver.com / m.blog.naver.com이에요.
- 미지원·잘못된 영상·내부 IP·localhost·메타데이터 주소는 수집 시 외부 호출 전에 SOURCE_FETCH_FAILED로 거부해요.

## 외부 호출 제한

- 실제 접속지는 코드에 고정된 `https://www.youtube.com/oembed` 하나예요. 검증한 영상 ID로 canonical watch URL을 만들고 인코딩해서 url 쿼리에 넣어요. 입력 호스트·공유 토큰·추가 쿼리·주소를 외부 요청의 목적지로 사용하지 않아요.
- 리디렉션은 같은 호스트를 포함해 전부 따라가지 않아요. 입력 주소와 응답의 Location·html·author_url·thumbnail_url로 추가 접속하지 않아요. 일반 URL을 가져오는 범용 HTTP 클라이언트가 아니에요.
- JDK의 기본 TLS 인증서·호스트 검증을 사용해요. 시스템 프록시·쿠키·인증을 전달하지 않아요. DNS는 고정된 신뢰 호스트만 사용하며 임의 호스트를 받아 DNS 검사 후 접속하는 기능은 없어요.
- 연결 2초, 요청 응답 4초, 전체 본문 완료 5초예요. 전체 시간 초과는 future를 취소하고, 본문이 느리게 계속 오는 경우도 제한해요. 재시도는 하지 않아요.
- 응답 본문은 실제 수신 바이트 기준 최대 64KiB예요. Content-Length 유무와 무관하게 초과하면 구독을 취소해요. JSON만 읽고 압축 응답은 사용하지 않아요.
- 썸네일은 HTTPS i.ytimg.com의 해당 영상 `/vi/{id}/...jpg` 또는 `/vi_webp/{id}/...webp`만 전달해요. 썸네일 바이너리는 여기서 가져오지 않으며, 이후 사진 복사 단계에서도 별도로 검사해야 해요.
- 원문 URL·메타데이터 본문·네트워크 예외 원문을 로그나 오류 응답에 넣지 않아요.

## 실패 구분

| 상황 | 처리 |
| --- | --- |
| 잘못된 주소·미지원 플랫폼/영상 경로 | SOURCE_FETCH_FAILED |
| oEmbed 400·401·403·404·410 | 접근 불가로 SOURCE_FETCH_FAILED |
| 3xx·429·5xx, DNS·연결·시간 초과 | 썸네일 없이 FetchedContent를 반환해 Gemini 분석을 계속할 수 있어요 |
| 잘못된 JSON·형식·썸네일, 과대 본문·HTML·압축 응답 | 썸네일만 생략해요 |
| 작업 인터럽트·취소 | 인터럽트를 유지하고 수집 실패로 중단해요 |

oEmbed 접근 제한과 실제 영상 접근 가능 여부는 다를 수 있어요. 위 접근 불가 응답을 링크 실패로 처리하는 것은 이번 수집 정책이에요. 메타데이터만 실패해 분석을 계속한 경우, 실제 Gemini 실패는 기존 AI_EXTRACTION_FAILED 규칙을 따라요.

## 검증과 데모 확인

고정 응답으로 일반·쇼츠·단축 URL, 플랫폼 경계·내부 주소, 접근 불가/썸네일만 실패, 응답 크기·전체 시간 제한을 검사해요. 로컬 HTTP 서버와 실제 JDK 전송으로 청크 본문 제한·404 예외·내부 주소 리디렉션 미추적·헤더 이후 느린 본문 취소도 확인해요. 실제 YouTube·Gemini는 자동 테스트에서 호출하지 않아요.

기존 Gemini fixture로 수집 결과→Gemini 요청→카드 파싱을 이어서 원본 sourceUrl과 sourceTimestampSec=42가 유지되는지 확인해요. 전체 테스트 명령은 `./gradlew test verifyMockIsolation`이에요.

아래 실서비스 확인은 아직 하지 않았어요. 배포 설정을 변경하지 않았고, #23 연결 이후 로컬·Cloud Run 양쪽에서 같은 데모 쇼츠로 확인해요.

- [ ] 데모용 공개 여행 쇼츠의 oEmbed 제목·썸네일 응답과 실제 지연을 확인해요.
- [ ] Gemini 키·모델·영상 길이·할당량을 준비하고 접수→완료→카드 초안의 장소·타임스탬프를 확인해요.
- [ ] Cloud Run의 외부 HTTPS/DNS 접근과 실제 요청 제한을 확인해요. 메타데이터가 실패해도 영상 분석이 이어지는지 봐요.
- [ ] 비공개/삭제 영상 실패와 썸네일 없는 영상, 다중 링크 부분 실패를 확인해요.

참고: [oEmbed 명세·YouTube 제공자 목록](https://oembed.com/), [JDK HttpClient 설정](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html), [본문 구독·취소 계약](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscriber.html).
