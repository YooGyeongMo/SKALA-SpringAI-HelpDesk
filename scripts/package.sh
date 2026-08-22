#!/usr/bin/env bash
# 제출물 패키징 — 코드 + 보고서 PDF 를 SpringAI_Final_P260.zip 으로 압축한다.
# 빌드 산출물·비밀·.git 은 제외한다.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="SpringAI_Final_P260.zip"
PDF="docs/SpringAI_Final_P260_보고서.pdf"

[ -f "$PDF" ] || { echo "❌ 보고서 PDF가 없습니다: $PDF"; exit 1; }

rm -f "$OUT"
zip -r -q "$OUT" . \
  -x '*/build/*' -x 'build/*' \
  -x '*/bin/*' -x 'bin/*' \
  -x '*/.gradle/*' -x '.gradle/*' \
  -x '*/.git/*' -x '.git/*' \
  -x '.env' -x '*.zip' \
  -x 'docs/PLAN.md' -x 'docs/GIT_WORKFLOW.md' \
  -x '*/.DS_Store' -x '.DS_Store'

echo "✅ 생성됨: $OUT"
unzip -l "$OUT" | tail -3
echo "포함 확인:"
unzip -l "$OUT" | grep -E "보고서.pdf|build.gradle|HelpDeskApplication|README" | sed 's/^/  /'
