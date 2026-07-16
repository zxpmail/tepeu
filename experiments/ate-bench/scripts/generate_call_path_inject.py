# -*- coding: utf-8 -*-
"""从 Tools.java + 核心三类 + AGENT_CALL_PATH.md 生成 system-with-call-path.txt。

对应文章「同步债 → CI 生成」：改注册表或路径文档后重跑本脚本即可更新 inject。
"""
from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
BENCH_ROOT = SCRIPT_DIR.parent
REPO_ROOT = BENCH_ROOT.parent.parent

PREAMBLE = """\
You are modifying a Java / Spring AI codebase (Tepeu). Complete the given task exactly.

Rules:
- Read existing code before editing.
- Do not refactor unrelated files.
- Prefer the smallest change that satisfies the success criteria in the task.
- If the task asks for a written answer, write it only to the path specified in the task.
- Do not invent Spring annotations or layers that are not already used in this repo unless the task requires a new tool class.

## Authoritative call path (read this first; do not rediscover from scratch)

The following document is the project's agent call path. Prefer it when answering call-chain questions:
"""

FOOTER_TEMPLATE = """
(Full file also exists at backend/src/main/java/com/tepeu/agent/AGENT_CALL_PATH.md.)
(Registered tools from Tools.java: {tools}.)
"""


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def parse_registered_tools(tools_java: str) -> list[str]:
    names = re.findall(r'registry\.register\(\s*"([^"]+)"', tools_java)
    return names


def assert_core_symbols(repo: Path) -> None:
    checks = [
        (repo / "backend/src/main/java/com/tepeu/controller/ChatController.java", r"\bstream\s*\("),
        (repo / "backend/src/main/java/com/tepeu/agent/AgentOrchestrator.java", r"\bstreamTurn\s*\("),
        (
            repo / "backend/src/main/java/com/tepeu/service/chat/ChatService.java",
            r"\bstreamWithTools\s*\(",
        ),
    ]
    missing: list[str] = []
    for path, pattern in checks:
        if not path.is_file():
            missing.append(f"missing file {path.relative_to(repo)}")
            continue
        text = path.read_text(encoding="utf-8")
        if not re.search(pattern, text):
            missing.append(f"symbol not found in {path.relative_to(repo)}: {pattern}")
    if missing:
        raise SystemExit("Core call-path symbols missing:\n  - " + "\n  - ".join(missing))


def build_inject(repo: Path, call_path_md: Path, tools_java: Path) -> tuple[str, str]:
    assert_core_symbols(repo)
    md = call_path_md.read_text(encoding="utf-8").strip() + "\n"
    tools_src = tools_java.read_text(encoding="utf-8")
    tools = parse_registered_tools(tools_src)
    if not tools:
        raise SystemExit(f"No registry.register(...) found in {tools_java}")

    # Fingerprint covers both human path doc and machine registry — either change invalidates inject.
    fp_src = md + "\n---\n" + "\n".join(tools)
    fingerprint = sha256_text(fp_src)

    body = (
        f"<!-- inject-sync: sha256={fingerprint} tools={','.join(tools)} -->\n"
        + PREAMBLE
        + "\n"
        + md
        + FOOTER_TEMPLATE.format(tools=", ".join(tools))
    )
    return body, fingerprint


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate system-with-call-path.txt from sources")
    ap.add_argument("--repo", type=Path, default=REPO_ROOT, help="Tepeu repo root")
    ap.add_argument(
        "--out",
        type=Path,
        default=BENCH_ROOT / "prompt" / "system-with-call-path.txt",
        help="Output inject file",
    )
    ap.add_argument(
        "--call-path",
        type=Path,
        default=None,
        help="Override AGENT_CALL_PATH.md path",
    )
    ap.add_argument(
        "--tools-java",
        type=Path,
        default=None,
        help="Override Tools.java path",
    )
    ap.add_argument("--check", action="store_true", help="Only check; do not write (exit 1 if stale)")
    ap.add_argument(
        "--print-fingerprint",
        action="store_true",
        help="Print fingerprint and exit 0",
    )
    args = ap.parse_args()

    repo = args.repo.resolve()
    call_path = (args.call_path or repo / "backend/src/main/java/com/tepeu/agent/AGENT_CALL_PATH.md").resolve()
    tools_java = (args.tools_java or repo / "backend/src/main/java/com/tepeu/agent/Tools.java").resolve()
    out = args.out.resolve()

    if not call_path.is_file():
        raise SystemExit(f"Missing call path doc: {call_path}")
    if not tools_java.is_file():
        raise SystemExit(f"Missing Tools.java: {tools_java}")

    body, fingerprint = build_inject(repo, call_path, tools_java)

    if args.print_fingerprint:
        print(fingerprint)
        return 0

    if args.check:
        if not out.is_file():
            print(f"STALE: missing {out}", file=sys.stderr)
            return 1
        current = out.read_text(encoding="utf-8")
        if current != body:
            print(f"STALE: {out} out of sync with Tools.java / AGENT_CALL_PATH.md", file=sys.stderr)
            print(f"expected fingerprint={fingerprint}", file=sys.stderr)
            m = re.search(r"inject-sync: sha256=([0-9a-f]+)", current)
            if m:
                print(f"file fingerprint={m.group(1)}", file=sys.stderr)
            print("Run: python experiments/ate-bench/scripts/generate_call_path_inject.py", file=sys.stderr)
            return 1
        print(f"OK: inject in sync (sha256={fingerprint})")
        return 0

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(body, encoding="utf-8", newline="\n")
    print(f"Wrote {out} (sha256={fingerprint})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
