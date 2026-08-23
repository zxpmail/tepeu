#!/usr/bin/env bash
# PostToolUse: compact task-history.md (UTF-8, dedupe, keep 25). Silent.
set -euo pipefail
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$PWD}"
SCRIPT="$(cd "$(dirname "$0")" && pwd)/compact-task-history.py"
if [[ ! -f "$PROJECT_DIR/memory/task-history.md" ]]; then
  exit 0
fi
if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
  exit 0
fi
if command -v python3 >/dev/null 2>&1; then
  python3 "$SCRIPT" || true
else
  python "$SCRIPT" || true
fi
exit 0
