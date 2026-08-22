#!/usr/bin/env bash
# 깃 훅 설치 — 어떤 환경(맥/리눅스/새 클론)에서든 이 한 번이면 끝
set -euo pipefail
cd "$(dirname "$0")/.."

chmod +x .githooks/* 2>/dev/null || true
git config core.hooksPath .githooks
echo "✅ 깃 훅 설치 완료 (pre-commit: 키 유출·대용량·컴파일 검사 / commit-msg: 컨벤션 검사)"
