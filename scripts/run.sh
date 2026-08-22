#!/usr/bin/env bash
# .env 의 OPENAI_API_KEY 를 로드하고 앱 실행
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "❌ .env 파일이 없습니다.  cp .env.example .env  후 OPENAI_API_KEY 를 넣어 주세요."
  exit 1
fi
set -a; source .env; set +a

if [[ "${OPENAI_API_KEY:-}" == "" || "${OPENAI_API_KEY:-}" == sk-여기에* ]]; then
  echo "❌ .env 의 OPENAI_API_KEY 가 비어 있습니다."
  exit 1
fi

echo "▶ HelpDesk AI 기동 — http://localhost:8080/swagger-ui.html"
./gradlew bootRun
