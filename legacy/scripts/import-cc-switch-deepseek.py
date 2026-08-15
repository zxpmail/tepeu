#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从本机 CC Switch（~/.cc-switch/cc-switch.db）读取 DeepSeek 配置，写入 Tepeu /api/provider。
密钥只走本机内存与 Tepeu 加密库，不写入仓库文件。

用法:
  python scripts/import-cc-switch-deepseek.py
  python scripts/import-cc-switch-deepseek.py --api http://127.0.0.1:30141
"""
from __future__ import annotations

import argparse
import json
import sqlite3
import sys
import urllib.error
import urllib.request
from pathlib import Path


def load_deepseek_from_cc_switch() -> dict:
    db = Path.home() / ".cc-switch" / "cc-switch.db"
    if not db.is_file():
        raise FileNotFoundError(f"未找到 CC Switch 数据库: {db}")

    conn = sqlite3.connect(str(db))
    conn.row_factory = sqlite3.Row
    rows = conn.execute(
        "SELECT id, name, settings_config, is_current FROM providers "
        "WHERE lower(name) LIKE '%deepseek%' ORDER BY is_current DESC, created_at DESC"
    ).fetchall()
    conn.close()
    if not rows:
        raise RuntimeError("CC Switch 中未找到名为 DeepSeek 的服务商")

    cfg = json.loads(rows[0]["settings_config"] or "{}")
    env = cfg.get("env") or {}
    api_key = (
        env.get("ANTHROPIC_AUTH_TOKEN")
        or env.get("ANTHROPIC_API_KEY")
        or env.get("DEEPSEEK_API_KEY")
        or ""
    ).strip()
    base_url = (
        env.get("ANTHROPIC_BASE_URL")
        or "https://api.deepseek.com/anthropic"
    ).strip()
    model = (
        env.get("ANTHROPIC_MODEL")
        or env.get("ANTHROPIC_DEFAULT_SONNET_MODEL")
        or "deepseek-v4-flash"
    ).strip()

    if not api_key:
        raise RuntimeError("DeepSeek 条目存在但未找到 API Key（ANTHROPIC_AUTH_TOKEN）")

    return {
        "apiKey": api_key,
        "baseUrl": base_url,
        "defaultModel": model,
        "enabled": True,
        "providerId": "deepseek",
    }


def put_provider(api: str, body: dict) -> None:
    url = api.rstrip("/") + "/api/provider/config/deepseek"
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="PUT",
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            payload = json.loads(res.read().decode("utf-8"))
    except urllib.error.URLError as e:
        raise RuntimeError(
            f"无法连接 Tepeu API ({url})。请先启动后端。原始错误: {e}"
        ) from e

    if payload.get("code") != "OK":
        raise RuntimeError(f"保存失败: {payload}")


def main() -> int:
    parser = argparse.ArgumentParser(description="从 CC Switch 导入 DeepSeek 到 Tepeu")
    parser.add_argument("--api", default="http://127.0.0.1:30141", help="Tepeu API 根地址")
    parser.add_argument(
        "--print-meta",
        action="store_true",
        help="只打印脱敏元信息（不调用 API）",
    )
    parser.add_argument(
        "--emit-json",
        action="store_true",
        help="向 stdout 输出完整 JSON（供 E2E 读取；勿重定向到仓库文件）",
    )
    args = parser.parse_args()

    body = load_deepseek_from_cc_switch()

    if args.emit_json:
        sys.stdout.write(json.dumps(body, ensure_ascii=False))
        return 0

    key = body["apiKey"]
    masked = key[:8] + "***" if len(key) > 8 else "***"
    print(f"来源: CC Switch DeepSeek")
    print(f"Base URL: {body['baseUrl']}")
    print(f"Model: {body['defaultModel']}")
    print(f"API Key: {masked}")

    if args.print_meta:
        return 0

    put_provider(args.api, body)
    print(f"已写入 Tepeu: {args.api}/api/provider/config/deepseek")
    return 0



if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print(f"错误: {e}", file=sys.stderr)
        raise SystemExit(1)
