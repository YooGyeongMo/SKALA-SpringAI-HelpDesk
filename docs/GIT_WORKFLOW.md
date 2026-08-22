# Git 워크플로

## 브랜치 전략 — 트렁크 기반 + Phase 브랜치

```
main ← feat/p0-scaffold ← feat/p1-chatclient ← ... (Phase당 브랜치 1개, PR 1개)
```

- `main`: 항상 빌드 가능한 상태만 머지
- `feat/p{N}-{이름}`: Phase 단위 단기 브랜치 (예: `feat/p2-ingest`)
- 급한 수정: `fix/{이름}`

## 이슈 = Phase

Phase당 이슈 1개(#1~#9), 이슈 템플릿의 **완료 판정** 칸에 교재 검증 방법을 그대로 적는다.
PR 본문에 `Closes #N` 으로 연결.

## 커밋 컨벤션

```
feat: Phase 2 — 문서 인제스트 파이프라인 (재색인 + 메타데이터)
fix:  SSE 타임아웃 처리
test: OrderTools 권한 격리 단위 테스트
docs: REPORT 검증 캡처 정리
chore: gradle wrapper / 스크립트
```

- 제목은 한 줄 요약, 본문에 검증 방법(스크립트/curl)을 남긴다
- 커밋 전 확인: `./gradlew build` 성공 + 비밀(.env) 미포함

## PR 규칙

- PR 템플릿의 **Proof** 칸에 실행 결과(원인→결과)를 붙인다 — 제출 PDF의 재료가 된다
- 셀프 머지 허용(1인 과제), 단 체크리스트 완료 후
