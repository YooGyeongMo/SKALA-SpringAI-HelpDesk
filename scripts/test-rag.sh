#!/usr/bin/env bash
# F1 검증 — 인제스트(재색인)·검색 확인·출처 표기·근거 없으면 거절
# 사전 조건: 앱 실행 중 (scripts/run.sh)
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"

echo "━━━ [1/5] 인제스트 1회차 — 원인: 규정 문서 4종 색인 ━━━"
R1=$(curl -sf -X POST "$BASE/api/admin/ingest")
echo "$R1" | python3 -m json.tool
C1=$(echo "$R1" | python3 -c "import json,sys; print(sum(x['chunks'] for x in json.load(sys.stdin)))")

echo
echo "━━━ [2/5] 인제스트 2회차 — 결과: 청크 수가 같아야 재색인 정상 ━━━"
R2=$(curl -sf -X POST "$BASE/api/admin/ingest")
C2=$(echo "$R2" | python3 -c "import json,sys; print(sum(x['chunks'] for x in json.load(sys.stdin)))")
echo "1회차 청크 합=$C1 / 2회차 청크 합=$C2"
if [ "$C1" = "$C2" ]; then echo "✅ 재색인 정상 (중복 누적 없음)"; else echo "❌ 청크가 늘었다 — 재색인 실패"; exit 1; fi

echo
echo "━━━ [3/5] 검색 결과를 눈으로 — '반품 배송비는 누가 내나요' ━━━"
curl -sf "$BASE/api/admin/chunks" --get --data-urlencode "q=반품 배송비는 누가 내나요" -d topK=3 | python3 -m json.tool

echo
echo "━━━ [4/5] RAG 답변 + 출처 — '단순 변심 반품은 며칠 이내에 해야 하나요?' ━━━"
ANS=$(curl -sf -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H 'X-User-Id: user1' \
  -d '{"question":"단순 변심 반품은 며칠 이내에 해야 하나요?","sessionId":"rag-test"}')
echo "$ANS" | python3 -m json.tool
echo "$ANS" | python3 -c "
import json,sys
a=json.load(sys.stdin)
assert a['sources'], '출처가 비어 있음 — F1 실패'
print('✅ 출처 표기 확인:', [s['document'] for s in a['sources']])"

echo
echo "━━━ [5/5] 근거 없는 질문 — '해외 직구 관세 대납 되나요?' → 거절해야 정상 ━━━"
curl -sf -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H 'X-User-Id: user1' \
  -d '{"question":"해외 직구 관세 대납 되나요?","sessionId":"rag-test"}' | python3 -m json.tool

echo
echo "✅ test-rag 완료 — [4][5]는 P3 구현 후 통과"
