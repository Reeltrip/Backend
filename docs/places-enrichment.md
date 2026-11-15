# 장소 보강 준비 (#22)

2026-11-16 사용자가 결정을 위임해 기본값을 **false로 복원**했어요. 11/14 사용자 선택과 같이 계약상 저장 허용 예외가 확인되기 전에는 영구 보강을 하지 않아요. 11/15 BE 2는 누락 필드를 채워 카드 저장을 돕도록 true로 바꿨지만, 현재 구현에는 저장 기한·만료 삭제·표시 정책 검증이 없어 기본 활성화를 유지하지 않기로 했어요. [공식 정책](https://developers.google.com/maps/documentation/places/web-service/policies)은 place ID의 저장과 일부 좌표의 제한된 임시 캐시를 구분하며, 이 예외가 주소·리뷰 수·영업시간의 무기한 저장을 모두 허용한다고 가정하지 않아요. 2026-11-16 확인했어요.

링크 수집·LLM 초안 생성은 계속되고, 주소·좌표 등 비어 있는 필드를 사용자가 확인해 채워 저장해요. 필수가 빠진 AI 카드는 기존 POST /places의 skipped로 남아요. 기존 보관함 데이터를 삭제하지 않아요. 영업시간 100자·주소 255자 제한과 허용된 보강 코드는 유지해요. Places 사진의 Cloud Storage 영구 복사는 구현하지 않았어요.

배포 환경에 MAPS_PERSIST_ENRICHMENT=true가 명시돼 있으면 코드 기본값보다 우선해요. 이 PR은 Cloud Run 설정을 바꾸지 않으므로 실제 환경 값을 확인한 뒤, 허용 조건이 없으면 별도 승인된 운영 변경으로 false를 적용해야 해요. 다시 켜기 전 계약 예외·보관 기한·만료 삭제·FE 출처 표시를 확인해요. [사진 정책](https://developers.google.com/maps/documentation/places/web-service/place-photos)도 함께 확인해요.

GOOGLE_MAPS_API_KEY를 쓰는 공식 Places Text Search REST 클라이언트와 장소 6종 보강 코드를 준비했어요. 키는 헤더로만 보내며 HTTP 전체 5초·응답 1MiB·최대 20결과, 사진 API는 호출하지 않아요. 교통/팁은 검색하지 않고 장소 카드당 한 번·링크당 최대 10번이에요. 검색 없음·실패면 원래 초안을 유지해요. sourceLinkId/원본 URL/타임스탬프/도시·카드 내용과 null을 보존해요.

기본 상태에서도 출처를 실제 링크에 묶고 가능한 원본 썸네일을 대체로 사용해요. LLM이 비운 필드를 만들어내지 않아요. Google 사진 주소·API 키가 FE로 전달되지 않아요. 실제 Google 호출·사진 복사·계약 예외 확인·FE의 Google 출처 표시 검증은 남아 있어요. 새 의존성·DB 변경은 없어요.
