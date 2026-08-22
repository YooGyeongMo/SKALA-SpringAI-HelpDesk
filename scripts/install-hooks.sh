#!/usr/bin/env bash
# 깃 훅 설치 — 개발용(키 유출·컴파일·커밋 컨벤션 검사). 실행이나 채점과는 무관하다.
# 압축본처럼 .git 이 없는 환경에서는 조용히 건너뛴다.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ℹ️  git 저장소가 아니므로 훅 설치를 건너뜁니다 (압축본에서는 정상입니다)."
  exit 0
fi

chmod +x .githooks/* 2>/dev/null || true
git config core.hooksPath .githooks
echo "✅ 깃 훅 설치 완료 (pre-commit: 키 유출·컴파일 검사 / commit-msg: 컨벤션 검사)"
