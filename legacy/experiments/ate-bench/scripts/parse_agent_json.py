# -*- coding: utf-8 -*-
"""从 Claude Code --output-format json 的日志中提取指标（容忍 result 字段被乱码截断）。"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def extract_with_regex(text: str) -> dict:
    def grab(pat: str, cast=lambda x: x):
        m = re.search(pat, text)
        return cast(m.group(1)) if m else None

    model = None
    m = re.search(r'"modelUsage"\s*:\s*\{\s*"([^"]+)"', text)
    if m:
        model = m.group(1)

    return {
        "ok": True,
        "num_turns": grab(r'"num_turns"\s*:\s*(\d+)', int),
        "total_cost_usd": grab(r'"total_cost_usd"\s*:\s*([0-9.]+)', float),
        "duration_ms": grab(r'"duration_ms"\s*:\s*(\d+)', int),
        "is_error": grab(r'"is_error"\s*:\s*(true|false)', lambda s: s == "true"),
        "model": model,
    }


def main() -> int:
    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8", errors="replace")
    data = None
    try:
        data = json.loads(text.strip())
    except json.JSONDecodeError:
        for line in text.splitlines():
            s = line.strip()
            if s.startswith("{") and '"num_turns"' in s:
                try:
                    data = json.loads(s)
                    break
                except json.JSONDecodeError:
                    continue
    if data is None:
        out = extract_with_regex(text)
        if out.get("num_turns") is None and out.get("total_cost_usd") is None:
            print(json.dumps({"ok": False, "is_error": True}))
            return 1
        print(json.dumps(out, ensure_ascii=False))
        return 0

    model = None
    usage = data.get("modelUsage") or {}
    if isinstance(usage, dict) and usage:
        model = next(iter(usage.keys()))
    out = {
        "ok": True,
        "num_turns": data.get("num_turns"),
        "total_cost_usd": data.get("total_cost_usd"),
        "duration_ms": data.get("duration_ms"),
        "is_error": bool(data.get("is_error")),
        "model": model,
    }
    print(json.dumps(out, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
