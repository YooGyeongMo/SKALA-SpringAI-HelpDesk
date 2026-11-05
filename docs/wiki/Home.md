# HelpDesk AI Wiki

쇼핑몰 고객 상담과 업무 처리(주문 조회, 교환/환불 접수)를 맡는 Spring AI 기반 **AI 상담 및 업무 처리 서비스**의 설계 기록입니다.
README가 "무엇을 만들었나"라면, 이 Wiki는 "왜 그렇게 만들었나"와 "어떻게 확인했나"를 남깁니다.

## 한눈에

| 항목 | 내용 |
|---|---|
| 스택 | Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, pgvector (PostgreSQL 17), OpenAI gpt-4o-mini |
| 핵심 구조 | `ChatClient` 하나에 Advisor 체인 Audit(0) → TokenMeter(10) → SafeGuard(100) → Memory(200) → QuestionAnswer(300) |
| 업무 처리 | `@Tool` 주문 조회, 티켓 접수(PENDING). 승인(APPROVED)은 운영자 REST API로만 |
| 검증 | P95 2.47s, 평균 토큰 상한 3,000 이하, 레드팀 20/20, 단위 테스트 7개 |
| 형태 | 1인 프로젝트, Phase별 이슈 8개와 PR 11개 |

## 페이지

| 페이지 | 내용 |
|---|---|
| [Architecture](Architecture.md) | 구성 요소, 요청 흐름, RAG 인제스트, 업무 처리 흐름, 실행 환경 |
| [API](API.md) | 상담 API, SSE 이벤트 형식, 운영자 API |
| [Tradeoffs](Tradeoffs.md) | 결정 로그 (상황, 선택지, 결정, 비용, 다시 볼 조건) |
| [Troubleshooting](Troubleshooting.md) | 임계값 튜닝, SafeGuard 자가 차단, 캐시 0%, P95 0ms 등 |
| [Evaluation](Evaluation.md) | 요구사항별 검증 결과, 레드팀 20종 |
| [Schedule](Schedule.md) | 실제 타임라인, 이슈/PR/브랜치 운영 방식 |

## 설계 원칙 다섯 줄

1. 차단은 저장보다 앞 (SafeGuard가 Memory보다 바깥)
2. 권한은 쿼리와 도구 안 (`findOwned(orderId, userId)`, userId는 `ToolContext`로)
3. 되돌릴 수 없는 일은 사람이 확정 (승인 API는 도구 목록에 없음)
4. 컨트롤러는 AI를 모른다 (`ChatClient`는 `HelpDeskService` 뒤에만)
5. AI가 실패해도 화면은 산다 (재시도 3회, 폴백 모델, 안전 문구 + traceId)

[README로](../../README.md)
