#!/usr/bin/env bash
# 개발 환경 준비: 깃 훅 설치 + pgvector 컨테이너 기동 + 준비 대기
set -euo pipefail
cd "$(dirname "$0")/.."

echo "▶ 깃 훅 설치 (개발용, 없어도 실행에는 지장 없음)"
bash scripts/install-hooks.sh || true

echo "▶ pgvector 컨테이너 기동 (docker compose up -d)"
docker compose up -d

echo "▶ PostgreSQL 준비 대기..."
for i in $(seq 1 30); do
  if docker exec helpdesk-pgvector pg_isready -U springai >/dev/null 2>&1; then
    echo "✅ pgvector 준비 완료 (localhost:5432 / db=springai)"
    exit 0
  fi
  sleep 1
done
echo "❌ 30초 안에 준비되지 않음 — docker compose logs pgvector 확인"
exit 1
