#!/usr/bin/env bash
# F2 검증 — 주문 조회(권한 격리)·티켓 접수(승인 게이트)·감사 로그
# 사전 조건: 앱 실행 중 + 인제스트 완료
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"

ask() { # ask <userId> <question> <sessionId>
  curl -sf -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H "X-User-Id: $1" \
    -d "{\"question\":\"$2\",\"sessionId\":\"$3\"}"
}

echo "━━━ [1/5] 내 주문 조회 — 원인: user1이 '주문 12345 어디예요?' ━━━"
R=$(ask user1 "제 주문 12345 지금 어디예요?" tools-t1)
echo "$R" | python3 -m json.tool --no-ensure-ascii
echo "$R" | python3 -c "
import json,sys; a=json.load(sys.stdin)
assert a['toolUsed'], 'toolUsed=false — 도구가 안 불렸다'
print('✅ toolUsed=true — 도구 호출 확인')"

echo
echo "━━━ [2/5] 남의 주문 — 원인: user1이 user2의 99999 조회 시도 → 결과: 차단 문구 ━━━"
ask user1 "주문 99999 배송 상태 알려줘" tools-t2 | python3 -m json.tool --no-ensure-ascii

echo
echo "━━━ [3/5] 교환 접수 — 원인: '12345 사이즈 때문에 교환 접수해줘' → 결과: 티켓번호+승인 안내 ━━━"
ask user1 "주문 12345 사이즈가 안 맞아서 교환 접수해줘" tools-t3 | python3 -m json.tool --no-ensure-ascii

echo
echo "━━━ [4/5] 승인 게이트 — 대기 목록 확인 후 사람이 승인 ━━━"
PENDING=$(curl -sf "$BASE/api/admin/tickets/pending")
echo "$PENDING" | python3 -m json.tool --no-ensure-ascii
NO=$(echo "$PENDING" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['no'] if d else '')")
if [ -n "$NO" ]; then
  echo "→ 담당자 승인: $NO"
  curl -sf -X POST "$BASE/api/admin/tickets/$NO/approve" | python3 -m json.tool --no-ensure-ascii
  echo "→ 승인 후 대기 목록(비어야 정상):"
  curl -sf "$BASE/api/admin/tickets/pending"
  echo
fi

echo
echo "━━━ [5/5] 티켓 상태 조회 — '내 티켓 어떻게 됐어?' ━━━"
ask user1 "내 티켓 처리 어떻게 됐어?" tools-t3 | python3 -m json.tool --no-ensure-ascii

echo
echo "✅ test-tools 완료 — 감사 로그는 앱 로그의 AI_TOOL_AUDIT 라인 확인"
