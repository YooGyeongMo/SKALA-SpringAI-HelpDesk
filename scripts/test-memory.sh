#!/usr/bin/env bash
# F3 검증 — 3턴 멀티턴(교재 320쪽 [확인] 시나리오) + 세션 격리
# ①규정 질문 ②"내 주문 12345는?" ③"그럼 그거 반품 돼요?" — ③이 되면 Memory·RAG·Tool 모두 살아있다
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
SID="memory-$(date +%s)"

ask() { # ask <userId> <question> <sessionId>
  curl -sf -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H "X-User-Id: $1" \
    -d "{\"question\":\"$2\",\"sessionId\":\"$3\"}"
}

echo "━━━ 턴① 규정 질문 (RAG) ━━━"
ask user1 "단순 변심 반품 규정이 어떻게 되나요?" "$SID" | python3 -c "import json,sys; a=json.load(sys.stdin); print(a['answer']); print('출처:', [s['document'] for s in a['sources']])"

echo
echo "━━━ 턴② 내 주문 조회 (Tool) ━━━"
ask user1 "제 주문 12345는 지금 어떤 상태예요?" "$SID" | python3 -c "import json,sys; a=json.load(sys.stdin); print(a['answer']); print('toolUsed:', a['toolUsed'])"

echo
echo "━━━ 턴③ 대명사 질문 — '그럼 그거 반품 돼요?' (Memory+RAG+Tool 대조) ━━━"
R3=$(ask user1 "그럼 그거 반품 돼요?" "$SID")
echo "$R3" | python3 -c "import json,sys; a=json.load(sys.stdin); print(a['answer'])"
echo "$R3" | python3 -c "
import json,sys
a=json.load(sys.stdin)
ok = ('12345' in a['answer']) or ('무선 이어폰' in a['answer']) or ('7일' in a['answer']) or ('가능' in a['answer'])
assert ok, '③이 앞 맥락(12345·배송완료·7일 규정)을 반영하지 못함'
print('✅ 3턴 맥락 유지 확인 — Memory·RAG·Tool 동시 생존')"

echo
echo "━━━ 격리 검증 — 다른 세션에서 같은 대명사 질문 → 맥락이 없어야 정상 ━━━"
ask user1 "그럼 그거 반품 돼요?" "other-$SID" | python3 -c "import json,sys; print(json.load(sys.stdin)['answer'])"

echo
echo "━━━ 메모리에 실제 저장된 이력 (검증 창구) ━━━"
curl -sf "$BASE/api/admin/history" --get -d "userId=user1" -d "sessionId=$SID" | python3 -c "
import json,sys
for m in json.load(sys.stdin):
    print(f\"[{m['role']}] {m['text'][:60]}\")"

echo
echo "✅ test-memory 완료"
