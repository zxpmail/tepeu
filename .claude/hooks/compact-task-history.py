#!/usr/bin/env python3
"""Compact memory/task-history.md. UTF-8 only.

第二列是工作位置，不是会话编号。只去掉整行完全相同的重复。
超过 THRESHOLD 时，把最旧的移进归档，表里留下最新的 KEEP 条。
归档里已经有的整行不再追加。
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

KEEP = 25
THRESHOLD = 30
ROW = re.compile(r"^\|\s*(\d{4}-\d{2}-\d{2})\s*\|\s*([^|]+?)\s*\|")
HEADER = """# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
"""
ARCHIVE_HEADER = """# Task History Archive

> 整行相同才算同一条。由 compact-task-history.py 以 UTF-8 写入。

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
"""


def project_dir() -> Path:
    raw = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    return Path(raw)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def unique_rows(text: str) -> list[str]:
    """按整行去重。第二列相同的不同任务都保留。"""
    rows: list[str] = []
    seen: set[str] = set()
    for line in text.splitlines():
        if not ROW.match(line):
            continue
        key = line.strip()
        if key in seen:
            continue
        seen.add(key)
        rows.append(line.rstrip())
    return rows


def write_table(path: Path, header: str, rows: list[str]) -> None:
    body = header + "".join(row + "\n" for row in rows)
    path.write_text(body, encoding="utf-8", newline="\n")


def compact(root: Path) -> int:
    history = root / "memory" / "task-history.md"
    archive = root / "memory" / "task-history-archive.md"
    if not history.is_file():
        return 0

    live = unique_rows(read_text(history))
    archived: list[str] = []
    if archive.is_file():
        archived = unique_rows(read_text(archive))

    if len(live) > THRESHOLD:
        # 文件按时间从旧到新。留下末尾最新的，把前面多出来的送去归档。
        overflow, keep = live[:-KEEP], live[-KEEP:]
        have = {r.strip() for r in archived}
        for row in overflow:
            if row.strip() not in have:
                archived.append(row)
                have.add(row.strip())
        live = keep

    write_table(history, HEADER, live)
    if archived or archive.is_file():
        write_table(archive, ARCHIVE_HEADER, archived)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(compact(project_dir()))
    except Exception:
        sys.exit(0)
