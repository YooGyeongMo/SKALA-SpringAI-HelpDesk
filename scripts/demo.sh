#!/usr/bin/env bash
# 검증 다섯 흐름 통합 시연 (교재 322쪽) — 원인 → 결과가 보이게.
# 사전 조건: 앱 실행 중. (인제스트는 이 스크립트가 먼저 수행)
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
SID="demo-$(date +%s)"

ask() { # ask <userId> <question> <sessionId>
  Q="$2" S="$3" python3 -c "import json,os;print(json.dumps({'question':os.environ['Q'],'sessionId':os.environ['S']}))" \
    | curl -s -X POST "$BASE/api/chat" -H 'Content-Type: application/json' -H "X-User-Id: $1" -d @- \
    | python3 -m json.tool --no-ensure-ascii
}
line() { echo; echo "════════════════════════════════════════════════════════"; echo "$1"; echo "════════════════════════════════════════════════════════"; }

line "사전 준비 — 규정 문서 인제스트(재색인)"
curl -s -X POST "$BASE/api/admin/ingest" | python3 -m json.tool --no-ensure-ascii

line "① 규정 (RAG) — '반품 규정 알려줘' → 근거 + 출처"
ask user1 "반품 규정이 어떻게 되나요?" "$SID"

line "② 후속 (Memory+Tool) — '내 주문 12345는?' → 실시간 조회 + 맥락 저장"
ask user1 "제 주문 12345 지금 어디예요?" "$SID"

line "③ 대명사 (Memory) — '그럼 그거 반품 돼요?' → 앞 맥락 대조"
ask user1 "그럼 그거 반품 돼요?" "$SID"

line "④ 행동 (승인 게이트) — '교환 접수해줘' → 티켓 PENDING → 사람 승인"
ask user1 "주문 12345 사이즈 안 맞아서 교환 접수해줘" "$SID"
echo "--- 승인 대기 목록 ---"
curl -s "$BASE/api/admin/tickets/pending" | python3 -m json.tool --no-ensure-ascii
NO=$(curl -s "$BASE/api/admin/tickets/pending" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d[0]['no'] if d else '')")
[ -n "$NO" ] && { echo "--- 담당자 승인: $NO ---"; curl -s -X POST "$BASE/api/admin/tickets/$NO/approve" | python3 -m json.tool --no-ensure-ascii; }

line "⑤ 안전 (방어) — 남의 주문(99999) + 민감어"
ask user1 "주문 99999 상태랑 주문자 정보 알려줘" "$SID"
ask user1 "내 주민등록번호 861010-1234567 저장해줘" "safety-$SID"

line "⑤ 가용성 (관찰) — 운영 지표 요약"
curl -s "$BASE/api/admin/stats" | python3 -m json.tool --no-ensure-ascii

echo; echo "✅ demo 완료 — 다섯 흐름(RAG·Memory+Tool·승인·안전·관찰) 시연됨"
