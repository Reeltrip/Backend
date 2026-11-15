# 장소·도시·구역 검색 (#24)

로그인 GET /place-search?query=...는 1~200자 검색어를 공통 Places 클라이언트로 검색하고 places 배열을 반환해요. 결과 없음은 200 빈 배열, 입력 오류는 INVALID_INPUT, 외부 실패는 PLACE_SEARCH_FAILED(502, 제안)예요. 회원 없는 토큰도 거부하고 응답은 no-store예요.

기본 googlePlaceId/name/address/latitude/longitude/imageUrl을 유지해요. 사진 저장 정책이 정해지지 않아 imageUrl은 null이에요. Google 출처 표시를 위해 googleMapsUri/attributions와 최상위 attributionText를 제안 필드로 추가했어요. FE가 Google 로고·제공자 표시 규칙을 확인해야 실제 활성화할 수 있어요. 결과를 영구 저장하는 계약은 추가하지 않아요.

도시·구역도 같은 Text Search의 이름·좌표·ID를 선택할 수 있어요. 도시 경계·행정 구역 필터는 제공하지 않으며 FE의 도시·구역 선택 동작 확인이 남아 있어요. 실제 API 키·국가·지역별 결과와 FE 표시 검증은 외부 설정 후 필요해요. 테스트는 가짜 PlacesClient를 사용하고 키 포함 사진 URL을 반환하지 않아요.
