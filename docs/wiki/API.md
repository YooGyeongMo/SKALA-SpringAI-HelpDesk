# API

Swagger UI: `http://localhost:8080/swagger-ui.html`, OpenAPI JSON: `/v3/api-docs`

## 상담

### `POST /api/chat` 동기, 구조화 응답

| 헤더 | 설명 |
|---|---|
| `X-User-Id` | 사용자 ID (기본 `user1`). 인증을 대신하며 프롬프트가 아니라 `ToolContext`로만 전달 |

```json
{ "question": "단순 변심 반품은 며칠 이내에 해야 하나요?", "sessionId": "session-1" }
```

응답 (실제 실행 결과, `docs/images/03-swagger-execute.jpg`)

```json
{
  "answer": "반품 규정은 다음과 같습니다. 단순 변심의 경우 상품 배송 완료일로부터 7일 이내에 신청 가능하며, ...",
  "sources": [
    { "document": "반품규정.md", "version": "2026-08-22" },
    { "document": "교환환불FAQ.md", "version": "2026-08-22" },
    { "document": "배송정책.md", "version": "2026-08-22" },
    { "document": "회원등급혜택.md", "version": "2026-08-22" }
  ],
  "toolUsed": false,
  "cached": true
}
```

| 필드 | 의미 |
|---|---|
| `sources` | `QuestionAnswerAdvisor`가 실제로 넣은 근거 문서. 비어 있으면 문서 근거 없이 답한 것 |
| `toolUsed` | 이번 답변에서 도구가 호출됐는지 |
| `cached` | 시맨틱 캐시 적중 여부 (모델 미호출) |

오류 시: 입력 검증 실패는 400 `{"message": "question 값을 확인해 주세요."}`, 그 외는 503 `{"message": "잠시 후 다시 시도해 주세요.", "traceId": "..."}`. 상세는 로그에만 남깁니다.

### `POST /api/chat/stream` SSE

요청 본문은 같습니다. 이벤트 순서:

```
event:token    data:반품은
event:token    data: 배송 완료일로부터
...
event:sources  data:[{"document":"반품규정.md","version":"2026-08-22"}, ...]
event:done     data:[DONE]
```

curl로 볼 때는 `-N` 옵션이 필요합니다. 서버 쪽 타임아웃은 60초입니다.

## 운영자 (`/api/admin`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/ingest` | `policies/*.md` 전체 재색인. 결과는 문서별 청크 수 |
| GET | `/chunks?q=&topK=` | 질문별 검색 청크와 점수. 임계값 조정 근거 |
| GET | `/tickets/pending` | 승인 대기 티켓 |
| GET | `/tickets/approved` | 승인 완료 티켓 |
| POST | `/tickets/{no}/approve` | 승인 (사람 전용, 도구 목록에 없음) |
| GET | `/stats` | 질의 수, 토큰 합계, 질의당 평균 토큰과 예산 준수, 지연 P50/P95, 도구 호출 수, 캐시 적중률 |
| GET | `/history?userId=&sessionId=` | 메모리에 실제 저장된 대화 이력 |

## Actuator

`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`

## 시드 데이터

| 주문 | 소유자 | 상품 | 상태 |
|---|---|---|---|
| 12345 | user1 | 무선 이어폰 | 배송완료 (3일 전) |
| 12346 | user1 | 러닝화 265mm | 배송중 |
| 12347 | user1 | 블루투스 키보드 | 상품 준비중 |
| 99999 | user2 | 게이밍 모니터 | 배송완료 (권한 격리 검증용) |

[Home](Home.md)
