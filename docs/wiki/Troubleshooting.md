# Troubleshooting

공통 원칙은 "성공 메시지가 아니라 결과물로 확인한다"였습니다. 확인 창구(`/api/admin/chunks`, `/stats`, `/history`, 캐시 점수 로그)를 먼저 열고 숫자로 판단했습니다.

## 1. RAG 근거가 하나도 안 붙음

| | |
|---|---|
| 증상 | 규정 질문인데 `sources`가 비고 "확인되지 않습니다"로 답함 |
| 원인 | 임계값 0.62. 이 한국어 코퍼스와 `text-embedding-3-small`에서는 관련 질문 점수가 0.315~0.44, 무관 질문이 0.16~0.245 |
| 해결 | `helpdesk.rag.threshold: 0.28`. "반품 배송비 누가 내나요"가 `반품규정.md` 1위(0.439) |
| 확인 | `/api/admin/chunks?q=...` 점수, `scripts/test-rag.sh` |
| 관련 | PR #10, 이슈 #2 |

## 2. 모든 요청이 "민감정보" 차단

| | |
|---|---|
| 증상 | 평범한 규정 질문도 SafeGuard 차단 문구 |
| 원인 | Spring AI 1.1.8 `SafeGuardAdvisor`는 `prompt().getContents()`로 시스템 메시지까지 검사. 시스템 프롬프트 안전 원칙에 "주민등록번호"를 예시로 적어 둠 |
| 해결 | 시스템 프롬프트는 "신분증 번호, 결제 수단 번호"로 돌려 쓰고, 차단 단어는 `AiConfig` 코드에만. 설정 옆에 주석으로 남김 |
| 확인 | 정상 질문 통과, 민감어 질문 차단 (레드팀 5, 6번) |
| 관련 | PR #11, 이슈 #3 |

## 3. 시맨틱 캐시 적중률 0%

| | |
|---|---|
| 증상 | 같은 질문을 반복해도 `cached=false` |
| 원인 | 캐시용 `SimpleVectorStore` 점수 스케일이 압축됨. 같은 질문도 0.63 |
| 해결 | 유형별 실측 후 0.50 |
| 확인 | `scripts/test-cache.sh`: 같은 의도 재질문 적중, 교환(다른 의도) 미적중, 도구 답변은 저장 안 됨 |
| 관련 | PR #15, 이슈 #7 |

| 같은 질문 | 반품 유사 표현 | 반품 유사 의도 | 교환 | 배송 | 등급 |
|:-:|:-:|:-:|:-:|:-:|:-:|
| 0.63 | 0.55 | 0.51 | 0.38 | 0.36 | 0.26 |

## 4. 운영 콘솔 P95가 0ms로 초기화

| | |
|---|---|
| 증상 | 질의 없이 2분쯤 지나면 P95 0 |
| 원인 | Micrometer 백분위 히스토그램 기본 만료 2분, 버퍼 회전 |
| 해결 | `distributionStatisticExpiry(Duration.ofHours(1))`, `distributionStatisticBufferLength(1)` |
| 확인 | 질의 공백 후에도 P95 2,651ms 유지 |
| 관련 | PR #18 |

같은 PR에서 운영 콘솔을 새로고침하면 승인 이력이 사라지던 문제도 `/api/admin/tickets/approved`로 서버에서 조회하도록 고쳤습니다.

## 5. 폴백이 동작하지 않을 수 있는 함정

`@Retryable`, `@Recover`는 메인 클래스에 `@EnableRetry`가 없으면 조용히 무시됩니다. `HelpDeskApplication`에 명시하고, `failover` 프로파일(존재하지 않는 모델)로 실제 전환을 확인했습니다 (`scripts/test-fallback.sh`, PR #15).

## 6. SSE 경로에서 감사와 계측이 빠짐

직접 만든 Advisor가 `CallAdvisor`만 구현하면 스트리밍 호출에서는 체인에서 건너뜁니다. `AuditAdvisor`, `TokenMeterAdvisor` 모두 `StreamAdvisor`를 함께 구현했고, 스트림의 토큰 사용량은 마지막 조각에만 오므로 `doOnNext`에서 기록합니다 (PR #15).

## 7. `@RestControllerAdvice` 범위

예외 처리기의 `basePackages`를 실제 패키지 `com.skala.helpdesk.web`로 지정해 사용자에게는 안전 문구와 traceId만, 상세는 로그에만 남깁니다.

## 8. 압축본에서 `setup.sh` 중단

`.git`이 없는 폴더에서 훅 설치가 실패해 pgvector 기동까지 멈췄습니다. `install-hooks.sh`는 깃 저장소가 아니면 종료코드 0으로 건너뛰고, `setup.sh`는 훅 결과와 무관하게 기동합니다 (PR #19).

## 9. macOS에서 WebFlux 기동 시 DNS 리졸버 오류 로그

Apple Silicon에서 netty DNS 리졸버 ERROR 로그가 찍혀 `netty-resolver-dns-native-macos::osx-aarch_64`를 runtime 의존성으로 추가했습니다 (`build.gradle`).

[Home](Home.md)
