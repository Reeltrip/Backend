# 장소 보강 준비 (#22)

사용자 결정(2026-11-14)으로 Places 상세의 영구 보강은 기본 비활성화예요. MAPS_PERSIST_ENRICHMENT=false를 유지하며, Google 계약상 저장 허용 조건·기한·출처 표시가 확인되기 전에는 켜지 않아요. Places 사진의 Cloud Storage 영구 복사는 구현하지 않았어요. [공식 정책](https://developers.google.com/maps/documentation/places/web-service/policies)과 [사진 정책](https://developers.google.com/maps/documentation/places/web-service/place-photos)을 먼저 확인해야 해요.

GOOGLE_MAPS_API_KEY를 쓰는 공식 Places Text Search REST 클라이언트와 장소 6종 보강 코드를 준비했어요. 키는 헤더로만 보내며 HTTP 전체 5초·응답 1MiB·최대 20결과, 사진 API는 호출하지 않아요. 교통/팁은 검색하지 않고 장소 카드당 한 번·링크당 최대 10번이에요. 검색 없음·실패면 원래 초안을 유지해요. sourceLinkId/원본 URL/타임스탬프/도시·카드 내용과 null을 보존해요.

기본 상태에서도 출처를 실제 링크에 묶고 가능한 원본 썸네일을 대체로 사용해요. LLM이 비운 필드를 만들어내지 않아요. Google 사진 주소·API 키가 FE로 전달되지 않아요. 실제 Google 호출·사진 복사·계약 예외 확인·FE의 Google 출처 표시 검증은 남아 있어요. 새 의존성·DB 변경은 없어요.
