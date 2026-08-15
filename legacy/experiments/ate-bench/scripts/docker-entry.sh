#!/usr/bin/env bash
# Docker 入口：smoke=编译+裁判自检；full=跑完整 ATE（需 ANTHROPIC_API_KEY 或已挂载 claude 登录态）
set -euo pipefail
cd /workspace

echo "[ate] mode=${ATE_MODE:-smoke}"

echo "[ate] compile backend..."
(cd backend && mvn -q -DskipTests compile)

echo "[ate] unit-test IdempotencyService + ChatService..."
(cd backend && mvn -q -Dtest=IdempotencyServiceTest,ChatServiceTest,MemoryFileMirrorTest test)

echo "[ate] judge self-check on synthetic fixture..."
mkdir -p /tmp/ate-judge-fixture/ate-out
printf 'ChatController\nAgentOrchestrator\nChatService\n' > /tmp/ate-judge-fixture/ate-out/T1-call-chain.txt
python3 experiments/ate-bench/scripts/judge.py \
  --repo /tmp/ate-judge-fixture \
  --task experiments/ate-bench/tasks/T1-qa-call-chain.json

if [[ "${ATE_MODE}" == "full" ]]; then
  if [[ -z "${ANTHROPIC_API_KEY:-}" && ! -f /root/.claude/.credentials.json ]]; then
    echo "[ate] FULL mode needs ANTHROPIC_API_KEY or mounted ~/.claude credentials" >&2
    exit 2
  fi
  echo "[ate] full run not auto-started in CI-safe entry; use host pwsh scripts or extend this entry."
  echo "[ate] host example: pwsh -File experiments/ate-bench/scripts/run-ate.ps1"
  exit 0
fi

echo "[ate] smoke OK"
