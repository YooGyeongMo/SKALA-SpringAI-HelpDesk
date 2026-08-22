#!/usr/bin/env bash
# N4 검증 — 장애 주입: 주 모델을 존재하지 않는 모델로 바꿔(failover 프로파일) 폴백 동작을 확인한다.
# 주의: 실행 중인 앱을 내렸다가 failover 프로파일로 다시 띄운다. 끝나면 일반 모드로 복귀시킨다.
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="${BASE:-http://localhost:8080}"
LOG="/tmp/helpdesk-failover.log"

set -a; source .env; set +a

restart() { # restart <profile|"">
  lsof -ti :8080 | xargs kill 2>/dev/null || true
  sleep 2
  if [ -n "$1" ]; then SPRING_PROFILES_ACTIVE="$1" ./gradlew bootRun --console=plain > "$LOG" 2>&1 &
  else ./gradlew bootRun --console=plain > "$LOG" 2>&1 & fi
  for i in $(seq 1 40); do
    curl -s "$BASE/actuator/health" | grep -q UP && return 0
    sleep 1
  done
  echo "❌ 앱이 40초 안에 뜨지 않음"; exit 1
}

echo "━━━ [1/3] 장애 주입 — failover 프로파일(주 모델=gpt-none-exist-chaos)로 재기동 ━━━"
restart failover
curl -s -X POST "$BASE/api/admin/ingest" > /dev/null

echo "━━━ [2/3] 질문 → 주 모델 실패 → 폴백 모델 응답이 나와야 정상 ━━━"
curl -s -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H 'X-User-Id: user1' \
  -d '{"question":"단순 변심 반품 기간 알려줘","sessionId":"fallback-test"}' | python3 -m json.tool --no-ensure-ascii

echo
echo "→ 폴백 전환 로그:"
grep -E "주 모델 호출 실패|폴백 모델 응답 성공" "$LOG" | tail -4 || echo "(로그 미발견)"

echo
echo "━━━ [3/3] 일반 모드로 복귀 ━━━"
restart ""
echo "✅ test-fallback 완료 — 앱은 일반 모드로 재기동됨"
