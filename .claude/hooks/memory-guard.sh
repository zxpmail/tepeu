#!/usr/bin/env bash
# PostToolUse: memory guard — compaction + handoff tip
HOOK_DIR="$(cd "$(dirname "$0")" && pwd)"
bash "$HOOK_DIR/context-compaction.sh" || true
if [[ -f "$HOOK_DIR/check-handoff.sh" ]]; then
  bash "$HOOK_DIR/check-handoff.sh" || true
elif [[ -f "$HOOK_DIR/check-handoff.bat" ]]; then
  true
fi
exit 0
