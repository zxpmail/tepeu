# -*- coding: utf-8 -*-
"""Write a temporary Claude Code settings.json for ATE provider override."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# reuse decrypt helper
sys.path.insert(0, str(Path(__file__).resolve().parent))
from tepeu_provider_env import decrypt, load_master_key  # noqa: E402
import sqlite3


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--provider-id", default="anthropic")
    ap.add_argument("--model", default="")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument(
        "--db",
        type=Path,
        default=Path(__file__).resolve().parents[3] / "backend" / "tepeu.db",
    )
    ap.add_argument(
        "--master-key",
        type=Path,
        default=Path.home() / ".tepeu" / "master.key",
    )
    args = ap.parse_args()

    con = sqlite3.connect(args.db)
    row = con.execute(
        "SELECT api_key, base_url, default_model FROM llm_provider WHERE provider_id = ?",
        (args.provider_id,),
    ).fetchone()
    if not row:
        raise SystemExit(f"no provider {args.provider_id}")
    enc, base_url, db_model = row
    key = decrypt(enc, load_master_key(args.master_key))
    if key.strip().startswith("http"):
        raise SystemExit("api_key looks like URL")
    model = args.model or db_model or "glm-5.2"
    base_url = (base_url or "").strip() or "https://open.bigmodel.cn/api/anthropic"

    settings = {
        "env": {
            "ANTHROPIC_BASE_URL": base_url,
            "ANTHROPIC_AUTH_TOKEN": key,
            "ANTHROPIC_API_KEY": key,
            "ANTHROPIC_MODEL": model,
            "ANTHROPIC_DEFAULT_HAIKU_MODEL": model,
            "ANTHROPIC_DEFAULT_SONNET_MODEL": model,
            "ANTHROPIC_DEFAULT_OPUS_MODEL": model,
        },
        "model": model,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(settings, ensure_ascii=False, indent=2), encoding="utf-8")
    # never print secrets
    print(f"Wrote {args.out} base={base_url} model={model} key_len={len(key)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
