# ALRT-DETECT 기능 명세

## ALRT-DETECT-001 위기 표현 감지

> [[CRD-WRITE-003]](마음 기록 저장)와 [[BLK-SEND-002]](패치 발송)가 저장/발송 직전에 재사용하는 공용 판별 로직이다. 다만 사연 쪽은 저장 전에 미리 확인받을 수 있도록 `POST /api/v1/moods/stories`(CRD-WRITE-002)로 직접 호출 가능한 API로도 노출돼 있다 — 로그인 필요 API라 토큰 검사는 거치지만, 판별 결과 자체는 사용자를 구분하지 않는다.

**트리거**
[[CRD-WRITE-003]] 또는 [[BLK-SEND-002]] 처리 중 본문 검사 단계 진입

**사전조건**
검사할 본문 있음(사연 또는 패치 본문)

**입력**
- `content` (string): 검사 대상 본문(사연 300자 이내 또는 패치 50자 이내)

**처리 로직**
1. 저장/발송 직전 본문에서 위기 표현 여부 판별
2. 위기 표현 감지 시, 사연이면 저장 흐름은 유지하되 본인에게만 [[ALRT-HELP-002]] 위기 안내 노출, 패치면 발송을 보류하고 작성 글을 삭제
3. '돌아가기' 선택 시 사연은 원래 흐름([[CRD-WRITE-003]] 마음 기록 저장 → [[ITR-MAP-001]] 온기 지도 인원 표시)으로 합류, 패치는 빈 작성 화면으로 복귀

**출력**
위기 표현 감지 여부, [[ALRT-HELP-002]] 위기 안내 이동 여부

**예외 케이스**

| Status Code | Status Name | Description |
| --- | --- | --- |
| 401 | UNAUTHORIZED | 토큰 없음·위조·손상 — `POST /api/v1/moods/stories`로 직접 호출할 때만 |
| 401 | TOKEN_EXPIRED | Access 토큰 만료(재발급으로 무효화된 경우 포함) — `POST /api/v1/moods/stories`로 직접 호출할 때만 |
| 400 | INVALID_INPUT | 검사 대상 본문 없음 또는 300자 초과 (Bean Validation) |
| 500 | INTERNAL_SERVER_ERROR | 위기 표현 판별 처리 실패 |
