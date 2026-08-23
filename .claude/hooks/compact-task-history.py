#!/usr/bin/env python3
"""Compact memory/task-history.md. UTF-8 only. Dedupe by session id.

Keep the newest KEEP data rows. If unique rows exceed THRESHOLD, move the
oldest excess into memory/task-history-archive.md (also deduped, one table).
Does not re-append sessions already in the archive.
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

> One row per session id. Written UTF-8 by compact-task-history.py.

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
"""


def project_dir() -> Path:
    raw = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    return Path(raw)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def unique_rows(text: str) -> list[str]:
    rows: list[str] = []
    seen: set[str] = set()
    for line in text.splitlines():
        m = ROW.match(line)
        if not m:
            continue
        session = m.group(2).strip()
        if not session or session in seen:
            continue
        seen.add(session)
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
        keep, overflow = live[:KEEP], live[KEEP:]
        have = {ROW.match(r).group(2).strip() for r in archived if ROW.match(r)}
        for row in overflow:
            session = ROW.match(row).group(2).strip()
            if session not in have:
                archived.append(row)
                have.add(session)
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
