# SKALA HelpDesk AI — SpringAI 종합실습

사내 규정(**RAG**) · 실시간 주문/티켓(**Tool**) · 멀티턴(**Memory**) · **안전**(SafeGuard·승인 게이트·감사) · **관찰**(Micrometer) · **가용성**(폴백)을 하나의 요청 흐름으로 조립한 상담 어시스턴트.

> 설계·구현 계획 전체: [`docs/PLAN.md`](docs/PLAN.md) · Git 규칙: [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md)

## 기술 스택

Java 21 · Spring Boot 3.5.16 · Spring AI 1.1.8 · pgvector(PostgreSQL 17, Docker) · springdoc 2.8.6

## 빠른 시작

```bash
cp .env.example .env          # OPENAI_API_KEY 입력
scripts/setup.sh              # pgvector 컨테이너 기동
scripts/run.sh                # 앱 실행 → http://localhost:8080/swagger-ui.html
```

## 테스트 스크립트 (기능별 단독 실행)

| 스크립트 | 검증 내용 | 요구사항 |
|---|---|---|
| `scripts/test-rag.sh` | 인제스트(재색인)·검색 확인·출처 표기·근거 없으면 거절 | F1 |
| `scripts/test-tools.sh` | 내/남의 주문 격리, 티켓 접수(PENDING)→사람 승인 | F2 |
| `scripts/test-memory.sh` | 3턴 멀티턴 — "그럼 그거 반품 돼요?" | F3 |
| `scripts/test-safety.sh` | 레드팀 10종 + 감사 로그 | N3 |
| `scripts/test-fallback.sh` | 주 모델 장애 주입 → 폴백 응답 | N4 |
| `scripts/loadtest.sh` | P50/P95 응답 시간 | N1 |
| `scripts/demo.sh` | 검증 다섯 흐름 전체 시연 | 전체 |

## 아키텍처

```
요청 → ChatController → HelpDeskService → ChatClient
        └ Advisor 체인: Audit(0) → TokenMeter(10) → SafeGuard(100) → Memory(200) → QA(300)
          └ 모델 ↔ @Tool (OrderTools · TicketTools, 소유자 검증은 도구 안)
```

원칙: **차단은 저장보다 앞** · **권한은 쿼리·도구 안** · **컨트롤러는 AI를 모른다** · **AI가 실패해도 화면은 살린다**
