#!/usr/bin/env bash
# 가산점 — 시맨틱 캐시: 같은 의도 재질문은 즉시 응답(모델 미호출), 다른 의도는 캐시하지 않는다.
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"

ask() { # ask <userId> <question> <sessionId> → JSON
  Q="$2" S="$3" python3 -c "import json,os;print(json.dumps({'question':os.environ['Q'],'sessionId':os.environ['S']}))" \
    | curl -s -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H "X-User-Id: $1" -d @-
}
timed() { Q="$2" S="$3" python3 -c "import json,os;print(json.dumps({'question':os.environ['Q'],'sessionId':os.environ['S']}))" \
    | curl -s -o /dev/null -w "%{time_total}" -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H "X-User-Id: $1" -d @-; }

echo "━━━ [1] 최초 질문 — 캐시 미스 → 모델 호출 ━━━"
R=$(ask u1 "반품 규정이 어떻게 되나요?" cs1); echo "$R" | python3 -c "import json,sys;a=json.load(sys.stdin);print(' cached=',a['cached'],'| 소요는 모델호출')"
T1=$(timed u1b "반품 규정이 어떻게 되나요?" cs1b)

echo "━━━ [2] 같은 의도 재질문(다른 사용자·세션) — 캐시 적중 → 즉시 ━━━"
R2=$(ask u2 "반품 어떻게 하나요?" cs2); echo "$R2" | python3 -c "import json,sys;a=json.load(sys.stdin);print(' cached=',a['cached'])"
T2=$(timed u2b "반품 어떻게 하나요?" cs2b)
echo "  모델호출 소요=${T1}s / 캐시적중 소요=${T2}s"

echo "━━━ [3] 다른 의도(교환) — 캐시 미스가 정상(오적중 방지) ━━━"
ask u3 "교환은 어떻게 하나요?" cs3 | python3 -c "import json,sys;a=json.load(sys.stdin);print(' cached=',a['cached'],'(false 여야 정상)')"

echo "━━━ [4] 도구 사용 답변은 캐시하지 않는다(개인화·실시간 보호) ━━━"
ask u1 "제 주문 12345 어디예요?" cs4 > /dev/null
ask u9 "제 주문 12345 어디예요?" cs5 | python3 -c "import json,sys;a=json.load(sys.stdin);print(' cached=',a['cached'],'(false 여야 정상 — 남의 주문 캐시 누출 방지)')"

echo "=== 캐시 통계 ==="
curl -s "$BASE/api/admin/stats" | python3 -c "import json,sys;print(' ',json.load(sys.stdin)['semanticCache'])"
echo "✅ test-cache 완료"
