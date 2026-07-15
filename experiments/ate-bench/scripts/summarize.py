# -*- coding: utf-8 -*-
"""汇总 ATE summary.json，打印对照表。"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--summary", required=True)
    args = ap.parse_args()
    rows = json.loads(Path(args.summary).read_text(encoding="utf-8"))
    if isinstance(rows, dict):
        rows = [rows]

    by_task: dict[str, dict[str, dict]] = defaultdict(dict)
    for r in rows:
        by_task[r["task_id"]][r["variant"]] = r

    print("\n=== ATE 对照 ===")
    print(f"{'task':<28} {'base_ok':>8} {'exp_ok':>8} {'base_turns':>10} {'exp_turns':>10} {'base$':>8} {'exp$':>8}")
    for tid in sorted(k for k in by_task.keys() if k):
        b = by_task[tid].get("baseline", {})
        e = by_task[tid].get("experimental", {})
        bt = b.get("num_turns")
        et = e.get("num_turns")
        bc = b.get("total_cost_usd")
        ec = e.get("total_cost_usd")
        print(
            f"{tid:<28} "
            f"{str(b.get('passed')):>8} {str(e.get('passed')):>8} "
            f"{str(bt) if bt is not None else '-':>10} {str(et) if et is not None else '-':>10} "
            f"{bc if bc is not None else '-':>8} "
            f"{ec if ec is not None else '-':>8}"
        )

    def agg(variant: str):
        rs = [r for r in rows if r.get("variant") == variant]
        if not rs:
            return
        turns = [r["num_turns"] for r in rs if r.get("num_turns") is not None]
        costs = [r["total_cost_usd"] for r in rs if r.get("total_cost_usd") is not None]
        ok = sum(1 for r in rs if r.get("passed"))
        print(
            f"{variant}: success={ok}/{len(rs)} "
            f"avg_turns={(sum(turns)/len(turns) if turns else 'n/a')} "
            f"sum_cost={(sum(costs) if costs else 'n/a')}"
        )

    print("\n=== 合计 ===")
    agg("baseline")
    agg("experimental")


if __name__ == "__main__":
    main()
