#!/usr/bin/env python3
"""Tur-13 — canli voice_api /health (SALT OKUMA). Token .env'den adiyla okunur."""
import json
import os
import re
import sys
import urllib.request

BASES = ["http://127.0.0.1:8174", "http://192.168.1.101:8174"]
ENV = os.path.expanduser("~/.hermes/.env")


def token() -> str:
    with open(ENV, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*HERMES_DASHBOARD_SESSION_TOKEN\s*=\s*(.+)\s*$", line)
            if m:
                return m.group(1).strip().strip('"').strip("'")
    return ""


def main() -> int:
    tok = token()
    for base in BASES:
        try:
            req = urllib.request.Request(base + "/health", headers={"X-Hermes-Session-Token": tok})
            with urllib.request.urlopen(req, timeout=20) as r:
                body = json.loads(r.read().decode())
            print(f"{base} -> 200 {json.dumps(body, ensure_ascii=False)}")
            return 0
        except Exception as e:  # noqa: BLE001
            print(f"{base} -> HATA {e}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
