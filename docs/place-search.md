# 장소·도시·구역 검색 (#24)

로그인 GET /place-search?query=...는 1~200자 검색어를 공통 Places 클라이언트로 검색하고 places 배열을 반환해요. 결과 없음은 200 빈 배열, 입력 오류는 INVALID_INPUT, 외부 실패는 PLACE_SEARCH_FAILED(502, 제안)예요. 회원 없는 토큰도 거부하고 응답은 no-store예요.

기본 googlePlaceId/name/address/latitude/longitude/imageUrl을 유지해요. 사진 저장 정책이 정해지지 않아 imageUrl은 null이에요. Google 출처 표시를 위해 googleMapsUri/attributions와 최상위 attributionText를 제안 필드로 추가했어요. FE가 Google 로고·제공자 표시 규칙을 확인해야 실제 활성화할 수 있어요. 결과를 영구 저장하는 계약은 추가하지 않아요.

도시·구역도 같은 Text Search의 이름·좌표·ID를 선택할 수 있어요. 도시 경계·행정 구역 필터는 제공하지 않으며 FE의 도시·구역 선택 동작 확인이 남아 있어요. 실제 API 키·국가·지역별 결과와 FE 표시 검증은 외부 설정 후 필요해요. 테스트는 가짜 PlacesClient를 사용하고 키 포함 사진 URL을 반환하지 않아요.

## 2026-11-16 FE 연결 기준

[Frontdoor의 fakeAddressSearch.ts](https://github.com/Reeltrip/Frontdoor/blob/main/src/features/place/api/fakeAddressSearch.ts)는 주소 문자열 배열을 반환하고, [cardAdd.types.ts](https://github.com/Reeltrip/Frontdoor/blob/main/src/features/place/model/cardAdd.types.ts)는 현재 이름·주소·사진 등을 보관해요. [PlaceLocation](https://github.com/Reeltrip/Frontdoor/blob/main/src/features/place/model/place.types.ts)은 lat/lng를 사용해요. 이 날짜에 읽은 main 기준이며 FE 코드를 이번 PR에서 수정하거나 동작 확인하지 않았어요.

BE 응답은 문자열 배열이 아니라 `{ places, attributionText }`예요. FE는 인증된 요청으로 `/place-search?query=...`를 호출하고 선택한 결과의 아래 값을 유지해야 해요. 주소만 복사하면 계획·경로에 필요한 좌표가 사라져요.

| 검색 응답 | 직접 추가 카드 | 도시 선택 | 구역 선택 |
| --- | --- | --- | --- |
| googlePlaceId | googlePlaceId | cityPlaceId | 요청 필드 없음(FE 선택 상태에서만 보관) |
| name | name | city | name |
| address | address(없으면 직접 확인) | 표시 보조(전송하지 않음) | 표시 보조(전송하지 않음) |
| latitude / longitude | latitude / longitude | latitude / longitude | latitude / longitude |

FE의 지도 객체를 만들 때는 latitude → location.lat, longitude → location.lng로 변환하고, BE에 보낼 때는 해당 요청 DTO의 latitude/longitude를 사용해요. 계획 생성은 PlanCreateRequest의 city/cityPlaceId, 구역 저장은 PlanAreasUpdateRequest의 필드 이름을 따라요. 검색은 중심 좌표 선택이며 도시·구역 경계 검증을 제공하지 않아요.

imageUrl은 현재 null이에요. 검색 사진을 지어내거나 Google 사진을 Storage로 복사하지 않아요. 직접 추가 카드에는 사용자가 POST /images로 올린 주소를 선택해 넣어요. 주소가 없으면 직접 확인하고 필수 입력을 채워요. Google Maps 및 제공자 출처 표시는 검색 결과 옆에 유지하며 실제 FE 표시 방식은 리뷰가 필요해요.

query는 앞뒤 공백을 정리하고 1~200자로 보내요. 입력 형식 오류400, 미인증401, 외부 검색 실패502, 결과 없음200 빈 배열을 구분해요. 요청을 debounce하고 이전 검색을 취소하는 FE 연결을 권해요. BE 응답은 no-store이고 검색 결과를 영구 저장하는 별도 허용을 뜻하지 않아요.

Swagger에는 주소·사진·지도 링크의 nullable 상태를 명시했어요. API 필드나 실행 동작은 바꾸지 않았고 새 의존성·환경 변수·DB 변경도 없어요. 사용자 요청으로 추가 테스트·운영 실호출은 생략하고 컴파일/운영 JAR 빌드만 확인해요. 실제 FE 연결·사진 정책 합의까지 완료됐다고 보지는 않아요.
