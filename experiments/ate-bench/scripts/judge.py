# -*- coding: utf-8 -*-
"""确定性裁判：根据 task JSON 的 judge 字段判定任务是否成功。"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


def resolve_mvn() -> str:
    found = shutil.which("mvn") or shutil.which("mvn.cmd")
    if found:
        return found
    home = Path.home()
    candidates = [
        Path(r"C:\Program Files\Apache\maven\bin\mvn.cmd"),
        Path(os.environ.get("MAVEN_HOME", "")) / "bin" / "mvn.cmd",
        Path(os.environ.get("M2_HOME", "")) / "bin" / "mvn.cmd",
    ]
    for c in candidates:
        if c.is_file():
            return str(c)
    # 常见 scoop / chocolatey
    for p in Path(r"C:\ProgramData").glob("**/mvn.cmd"):
        return str(p)
    return "mvn"


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_file_contains_all(repo: Path, spec: dict) -> tuple[bool, str]:
    path = repo / spec["path"]
    if not path.is_file():
        return False, f"missing file {spec['path']}"
    text = read_text(path)
    missing = [s for s in spec["must_contain"] if s not in text]
    if missing:
        return False, f"missing strings: {missing}"
    return True, "ok"


def check_file_contains_any_group(repo: Path, spec: dict) -> tuple[bool, str]:
    path = repo / spec["path"]
    if not path.is_file():
        return False, f"missing file {spec['path']}"
    text = read_text(path)
    for i, group in enumerate(spec["groups"]):
        if not any(item in text for item in group):
            return False, f"group {i} not matched: {group}"
    return True, "ok"


def check_file_regex(repo: Path, spec: dict) -> tuple[bool, str]:
    pattern = re.compile(spec["regex"])
    if "glob" in spec:
        root = repo / spec["path"]
        files = list(root.glob(spec["glob"])) if root.is_dir() else []
        if not files:
            return False, f"no files match {spec['path']}/{spec['glob']}"
        for f in files:
            if pattern.search(read_text(f)):
                return True, f"matched in {f.relative_to(repo)}"
        return False, "regex not found in globbed files"
    path = repo / spec["path"]
    if not path.is_file():
        return False, f"missing file {spec['path']}"
    if pattern.search(read_text(path)):
        return True, "ok"
    return False, "regex not found"


def check_repo_grep(repo: Path, spec: dict) -> tuple[bool, str]:
    files = list(repo.glob(spec.get("glob", "**/*")))
    count = 0
    for f in files:
        if not f.is_file():
            continue
        try:
            text = read_text(f)
        except (UnicodeDecodeError, OSError):
            continue
        count += len(re.findall(spec["pattern"], text))
    need = int(spec.get("min_matches", 1))
    if count >= need:
        return True, f"matches={count}"
    return False, f"matches={count} < {need}"


def check_maven_compile(repo: Path, spec: dict) -> tuple[bool, str]:
    cwd = repo / spec.get("cwd", "backend")
    mvn = resolve_mvn()
    if mvn == "mvn" and not shutil.which("mvn") and not Path(r"D:\maven\bin\mvn.cmd").is_file():
        return False, "mvn not found"
    if Path(r"D:\maven\bin\mvn.cmd").is_file():
        mvn = str(Path(r"D:\maven\bin\mvn.cmd"))
    cmd = [mvn, "-q", "-DskipTests", "compile"]
    env = os.environ.copy()
    maven_bin = str(Path(mvn).parent)
    env["PATH"] = maven_bin + os.pathsep + env.get("PATH", "")
    try:
        if os.name == "nt":
            proc = subprocess.run(
                subprocess.list2cmdline(cmd),
                cwd=str(cwd),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=600,
                shell=True,
                env=env,
            )
        else:
            proc = subprocess.run(
                cmd,
                cwd=str(cwd),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=600,
                env=env,
            )
    except FileNotFoundError:
        return False, f"mvn not found (tried {mvn})"
    except subprocess.TimeoutExpired:
        return False, "mvn timeout"
    if proc.returncode == 0:
        return True, "compile ok"
    err = (proc.stderr or proc.stdout or "")[-500:]
    return False, f"compile failed: {err}"


def run_check(repo: Path, spec: dict) -> tuple[bool, str]:
    t = spec["type"]
    if t == "all":
        notes = []
        for child in spec["checks"]:
            ok, note = run_check(repo, child)
            notes.append(f"{child.get('type','?')}:{note}")
            if not ok:
                return False, "; ".join(notes)
        return True, "; ".join(notes)
    if t == "file_contains_all":
        return check_file_contains_all(repo, spec)
    if t == "file_contains_any_group":
        return check_file_contains_any_group(repo, spec)
    if t == "file_regex":
        return check_file_regex(repo, spec)
    if t == "repo_grep":
        return check_repo_grep(repo, spec)
    if t == "maven_compile":
        return check_maven_compile(repo, spec)
    return False, f"unknown judge type {t}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True)
    ap.add_argument("--task", required=True, help="path to task json")
    args = ap.parse_args()
    repo = Path(args.repo).resolve()
    task = json.loads(Path(args.task).read_text(encoding="utf-8"))
    ok, note = run_check(repo, task["judge"])
    out = {"task_id": task["id"], "passed": ok, "note": note}
    print(json.dumps(out, ensure_ascii=False))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
