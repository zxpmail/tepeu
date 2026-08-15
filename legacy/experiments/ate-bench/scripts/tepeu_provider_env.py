# -*- coding: utf-8 -*-
"""从 Tepeu SQLite 解密指定 provider 的 api_key，写出可 source 的 env 片段（stdout）。

用法（PowerShell）:
  python tepeu_provider_env.py --provider-id anthropic | Out-File -Encoding ascii .ate-env.ps1
  # 或：python tepeu_provider_env.py --provider-id anthropic --export-ps1
永不把明文 key 写入仓库；临时文件用完应删除。
"""
from __future__ import annotations

import argparse
import base64
import sqlite3
import sys
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PREFIX = "enc:v1:"
IV_BYTES = 12


def load_master_key(path: Path) -> bytes:
    raw = path.read_text(encoding="utf-8").strip()
    key = base64.b64decode(raw)
    if len(key) != 32:
        raise SystemExit(f"bad master key length {len(key)} at {path}")
    return key


def decrypt(stored: str, master: bytes) -> str:
    if not stored or not stored.startswith(PREFIX):
        return stored or ""
    combined = base64.b64decode(stored[len(PREFIX) :])
    iv, ct = combined[:IV_BYTES], combined[IV_BYTES:]
    return AESGCM(master).decrypt(iv, ct, None).decode("utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--provider-id", default="anthropic")
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
    ap.add_argument(
        "--export-ps1",
        action="store_true",
        help="Print PowerShell $env: assignments (for iex)",
    )
    ap.add_argument(
        "--check-only",
        action="store_true",
        help="Print base_url/model/key_len only; no secret",
    )
    args = ap.parse_args()

    if not args.db.is_file():
        raise SystemExit(f"missing db {args.db}")
    if not args.master_key.is_file():
        raise SystemExit(f"missing master key {args.master_key}")

    con = sqlite3.connect(args.db)
    row = con.execute(
        "SELECT api_key, base_url, default_model FROM llm_provider WHERE provider_id = ?",
        (args.provider_id,),
    ).fetchone()
    if not row:
        raise SystemExit(f"no provider {args.provider_id}")
    enc_key, base_url, model = row
    master = load_master_key(args.master_key)
    key = decrypt(enc_key, master)
    if args.check_only:
        print(f"provider_id={args.provider_id}")
        print(f"base_url={base_url}")
        print(f"model={model}")
        print(f"key_len={len(key)}")
        print(f"key_looks_like_url={key.strip().startswith('http')}")
        return 0

    if key.strip().startswith("http"):
        raise SystemExit("api_key looks like a URL — fix Tepeu Provider settings first")

    base_url = (base_url or "").strip() or "https://open.bigmodel.cn/api/anthropic"
    model = (model or "").strip() or "glm-5.2"

    if args.export_ps1:
        # Escape for single-quoted PowerShell strings
        def sq(s: str) -> str:
            return s.replace("'", "''")

        print(f"$env:ANTHROPIC_BASE_URL = '{sq(base_url)}'")
        print(f"$env:ANTHROPIC_AUTH_TOKEN = '{sq(key)}'")
        print(f"$env:ANTHROPIC_API_KEY = '{sq(key)}'")
        print(f"$env:ANTHROPIC_MODEL = '{sq(model)}'")
        for slot in (
            "ANTHROPIC_DEFAULT_HAIKU_MODEL",
            "ANTHROPIC_DEFAULT_SONNET_MODEL",
            "ANTHROPIC_DEFAULT_OPUS_MODEL",
        ):
            print(f"$env:{slot} = '{sq(model)}'")
        return 0

    # default: KEY=value for dotenv-like (still avoid logging)
    print(f"ANTHROPIC_BASE_URL={base_url}")
    print(f"ANTHROPIC_AUTH_TOKEN={key}")
    print(f"ANTHROPIC_MODEL={model}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
