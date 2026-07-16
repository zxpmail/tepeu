# -*- coding: utf-8 -*-
"""检查 call-path inject 是否与 Tools.java / AGENT_CALL_PATH.md 同步。

模式：
  fail     — 不同步则 exit 1（CI / 本地门禁）
  warn     — 不同步则告警但 exit 0
  degrade  — 不同步则打印 DEGRADE=1（ATE 应跳过过期 inject，仅用注册）
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
GENERATOR = SCRIPT_DIR / "generate_call_path_inject.py"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--mode",
        choices=("fail", "warn", "degrade"),
        default="fail",
        help="Action when inject is stale",
    )
    ap.add_argument("--repo", type=Path, default=None)
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    cmd = [sys.executable, str(GENERATOR), "--check"]
    if args.repo is not None:
        cmd.extend(["--repo", str(args.repo)])
    if args.out is not None:
        cmd.extend(["--out", str(args.out)])

    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    if proc.stdout:
        print(proc.stdout, end="")
    if proc.returncode == 0:
        if args.mode == "degrade":
            print("DEGRADE=0")
        return 0

    msg = (proc.stderr or proc.stdout or "inject stale").rstrip()
    if args.mode == "fail":
        print(msg, file=sys.stderr)
        return 1
    if args.mode == "warn":
        print(f"WARNING: {msg}", file=sys.stderr)
        return 0
    # degrade
    print(f"WARNING: {msg}", file=sys.stderr)
    print("DEGRADE=1")
    print(
        "Hint: skip system-with-call-path.txt; use system.txt or registry-only prompt.",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
