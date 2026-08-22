#!/usr/bin/env bash
# N1 검증 — /api/chat 를 N회 호출해 P50/P95 응답 시간을 계산한다.
# 사전 조건: 앱 실행 중 + 인제스트 완료
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
N="${1:-20}"
QUESTIONS=("반품 규정 알려줘" "교환은 어떻게 하나요" "배송은 며칠 걸려요" "VIP 혜택이 뭐예요" "포인트 소멸 언제예요")

echo "▶ /api/chat 부하 테스트 — $N 회 (동기, 비스트리밍)"
TMP=$(mktemp)
for i in $(seq 1 "$N"); do
  Q="${QUESTIONS[$((RANDOM % ${#QUESTIONS[@]}))]}"
  PAYLOAD=$(Q="$Q" S="load-$i" python3 -c "import json,os; print(json.dumps({'question':os.environ['Q'],'sessionId':os.environ['S']}))")
  T=$(curl -s -o /dev/null -w "%{time_total}" -X POST "$BASE/api/chat" \
      -H 'Content-Type: application/json' -H 'X-User-Id: user1' -d "$PAYLOAD")
  echo "$T" >> "$TMP"
  printf "."
done
echo

python3 - "$TMP" <<'PY'
import sys
xs = sorted(float(l) for l in open(sys.argv[1]) if l.strip())
n = len(xs)
def pct(p):
    import math
    return xs[min(n-1, math.ceil(p/100*n)-1)]
print(f"\n요청 수: {n}")
print(f"  P50 : {pct(50):.2f}s")
print(f"  P95 : {pct(95):.2f}s   (요구: 5초 이내)")
print(f"  최대: {xs[-1]:.2f}s / 최소: {xs[0]:.2f}s / 평균: {sum(xs)/n:.2f}s")
print("✅ N1 통과" if pct(95) <= 5.0 else "⚠️ P95가 5초 초과")
PY
rm -f "$TMP"
