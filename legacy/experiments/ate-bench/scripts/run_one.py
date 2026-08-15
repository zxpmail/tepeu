# -*- coding: utf-8 -*-
"""跑单个 ATE 任务：调 Claude Code CLI，写 agent 日志，跑裁判，输出一行 JSON 指标。"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BENCH_ROOT = ROOT.parent
JUDGE = ROOT / "judge.py"
PARSE = ROOT / "parse_agent_json.py"


def apply_seed(repo: Path, task: dict, variant: str | None = None) -> None:
    """Optional task['seed'].copy: plant bugs/fixtures before the agent runs.

    Paths may contain ``{variant}`` (e.g. AgentOrchestrator.{variant}.java).
    """
    seed = task.get("seed") or {}
    copies = seed.get("copy") or []
    for item in copies:
        from_tmpl = item["from"]
        if "{variant}" in from_tmpl:
            if not variant:
                raise ValueError(f"seed path needs --variant: {from_tmpl}")
            from_tmpl = from_tmpl.format(variant=variant)
        src = (BENCH_ROOT / from_tmpl).resolve()
        dst = (repo / item["to"]).resolve()
        if not src.is_file():
            raise FileNotFoundError(f"seed missing: {src}")
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)


def resolve_claude() -> str:
    """Windows 上 npm 全局命令是 .cmd，subprocess 需显式找到。"""
    found = shutil.which("claude")
    if found:
        return found
    home = Path.home()
    for cand in (
        home / "AppData/Roaming/npm/claude.cmd",
        home / "AppData/Roaming/npm/claude",
    ):
        if cand.is_file():
            return str(cand)
    return "claude"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True)
    ap.add_argument("--task", required=True)
    ap.add_argument("--system-prompt-file", required=True)
    ap.add_argument("--agent-log", required=True)
    ap.add_argument("--max-budget-usd", type=float, default=1.5)
    ap.add_argument(
        "--variant",
        default=None,
        help="Fixture variant name for seed path templates ({variant})",
    )
    ap.add_argument(
        "--model",
        default=None,
        help="Override model id (sets ANTHROPIC_MODEL* env + claude --model)",
    )
    ap.add_argument(
        "--base-url",
        default=None,
        help="Override ANTHROPIC_BASE_URL (e.g. https://open.bigmodel.cn/api/anthropic)",
    )
    ap.add_argument(
        "--auth-token",
        default=None,
        help="Override ANTHROPIC_AUTH_TOKEN / ANTHROPIC_API_KEY for this run only",
    )
    ap.add_argument(
        "--settings-file",
        default=None,
        help="Claude --settings JSON (use with --bare to force 3P base_url/key)",
    )
    ap.add_argument(
        "--bare",
        action="store_true",
        help="Pass claude --bare (needed so settings env wins over ~/.claude/settings.json)",
    )
    args = ap.parse_args()

    task = json.loads(Path(args.task).read_text(encoding="utf-8"))
    repo = Path(args.repo)
    apply_seed(repo, task, variant=args.variant)
    agent_log = Path(args.agent_log)
    err_log = Path(str(agent_log).replace(".agent.json", ".agent.err.txt"))
    if err_log == agent_log:
        err_log = agent_log.with_suffix(".err.txt")

    claude = resolve_claude()
    # 用 *-file 传系统提示，避免 shell 拼接弄坏换行/引号
    cmd = [
        claude,
        "-p",
        task["prompt"],
        "--append-system-prompt-file",
        str(Path(args.system_prompt_file).resolve()),
        "--dangerously-skip-permissions",
        "--max-budget-usd",
        str(args.max_budget_usd),
        "--output-format",
        "json",
    ]
    if args.model:
        cmd.extend(["--model", args.model])
    if args.bare:
        cmd.append("--bare")
    if args.settings_file:
        cmd.extend(["--settings", str(Path(args.settings_file).resolve())])
    env = os.environ.copy()
    if args.base_url:
        env["ANTHROPIC_BASE_URL"] = args.base_url
    if args.auth_token:
        env["ANTHROPIC_AUTH_TOKEN"] = args.auth_token
        env["ANTHROPIC_API_KEY"] = args.auth_token
    if args.model:
        # 覆盖 settings.json / 壳层里写死的 flash，避免仍落到 deepseek-v4-flash
        for key in (
            "ANTHROPIC_MODEL",
            "ANTHROPIC_DEFAULT_HAIKU_MODEL",
            "ANTHROPIC_DEFAULT_SONNET_MODEL",
            "ANTHROPIC_DEFAULT_OPUS_MODEL",
        ):
            env[key] = args.model
    # Windows：必须 shell=True 才能跑 .cmd；且 args 必须是字符串，否则参数会丢光
    run_cmd: str | list[str]
    if os.name == "nt":
        run_cmd = subprocess.list2cmdline(cmd)
        use_shell = True
    else:
        run_cmd = cmd
        use_shell = False
    with open(agent_log, "wb") as out, open(err_log, "wb") as err:
        proc = subprocess.run(
            run_cmd,
            cwd=str(repo),
            stdin=subprocess.DEVNULL,
            stdout=out,
            stderr=err,
            shell=use_shell,
            env=env,
        )

    metrics = {"ok": False, "is_error": True, "num_turns": None, "total_cost_usd": None, "duration_ms": None, "model": None}
    try:
        parsed = subprocess.check_output([sys.executable, str(PARSE), str(agent_log)], text=True, encoding="utf-8")
        metrics = json.loads(parsed.strip())
    except Exception as e:  # noqa: BLE001
        metrics["note"] = f"parse_failed: {e}"

    judge = {"passed": False, "note": "judge not run"}
    try:
        jout = subprocess.check_output(
            [sys.executable, str(JUDGE), "--repo", str(repo), "--task", str(args.task)],
            text=True,
            encoding="utf-8",
            stderr=subprocess.STDOUT,
        )
        judge = json.loads(jout.strip().splitlines()[-1])
    except subprocess.CalledProcessError as e:
        try:
            judge = json.loads((e.output or "").strip().splitlines()[-1])
        except Exception:  # noqa: BLE001
            judge = {"passed": False, "note": (e.output or str(e))[-500:]}
    except Exception as e:  # noqa: BLE001
        judge = {"passed": False, "note": str(e)}

    row = {
        "task_id": task["id"],
        "category": task.get("category"),
        "passed": bool(judge.get("passed")),
        "judge_note": judge.get("note"),
        "num_turns": metrics.get("num_turns"),
        "total_cost_usd": metrics.get("total_cost_usd"),
        "duration_ms": metrics.get("duration_ms"),
        "is_error": bool(metrics.get("is_error")),
        "model": metrics.get("model"),
        "claude_exit": proc.returncode,
    }
    print(json.dumps(row, ensure_ascii=False))
    return 0 if row["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
