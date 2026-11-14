# 경로 조회 (#25)

GET /routes?from={placeId}&to={placeId}&departAt={ISO8601}는 로그인 회원의 카드 또는 기존 계획 읽기 멤버에게 보이는 노드·플랜B·숙소 카드만 허용해요. 카드 도메인의 PlaceRouteAccessService와 계획 도메인의 PlanAccessService를 거쳐 확인하고, 권한 없는 카드와 없는 카드는 같은 404예요. 멤버 추가·공유 권한 확대는 하지 않아요. BE2의 기존 멤버 읽기 규칙을 재사용하며 리뷰가 필요해요.

출발 시각을 생략하면 Clock의 현재 시각을 써요. Google Routes API에 TRANSIT/WALK/DRIVE를 각각 한 번 요청하고 가능한 결과 중 시간이 가장 짧은 것을 반환해요. DRIVE는 택시 승차 가능 여부가 아닌 차량 주행 시간 추정이에요. 각 호출 최대 5초·1MiB, 합계 최대 15초이며 국가·교통수단별 미지원 결과는 제외해요. 실제 지역별 결과는 키 설정 후 확인해야 해요.

응답은 totalMinutes/departAt/arriveAt/segments(mode,minutes,lineName,stopName)예요. 서울 +09:00으로 반환하고 요금·환승 수·길 안내는 추가하지 않아요. mode는 WALK/TAXI/SUBWAY/BUS/TRAIN/TRAM/TRANSIT를 제안하며 FE 표시 확인이 필요해요. 경로 없음·시간 초과는 ROUTE_NOT_FOUND예요.

캐시는 저장을 허용하는 제공자만 30초·최대 256개를 사용해요. 키는 회원·카드 ID·현재 좌표·출발 분 단위이며 매 요청마다 권한을 다시 검사해요. 실패는 캐시하지 않아요. [Google Routes 저장 정책](https://developers.google.com/maps/documentation/routes/policies) 확인 전 Google 제공자는 캐시를 쓰지 않아요. 새 DB 테이블은 없어요. API 응답도 no-store예요.

테스트는 가짜 RoutesClient·공식 JSON 고정 응답을 사용해 권한·공유 멤버·기본 시각·도착 시각·기한/실패·캐시 만료·권한 재확인을 확인해요. 실제 Maps 키·국가별 결과·FE 출처 표시와 공유 계획 리뷰는 남아 있어요.
