#!/usr/bin/env python3
"""Tur-12 kapanış kontrolü: Kahya gerçekten AÇIK bırakıldı mı (salt-okunur /health)."""
import json
import os
import re
import urllib.request

ENV = os.path.expanduser("~/.hermes/.env")
BASE = "http://192.168.1.101:8174"


def token():
    with open(ENV, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*HERMES_DASHBOARD_SESSION_TOKEN\s*=\s*(.+)\s*$", line)
            if m:
                return m.group(1).strip().strip('"').strip("'")
    return ""


req = urllib.request.Request(BASE + "/health", headers={"X-Hermes-Session-Token": token()})
with urllib.request.urlopen(req, timeout=20) as res:
    body = json.loads(res.read().decode("utf-8"))
print("son /health:", json.dumps(body, ensure_ascii=False))
print("kahya:", body.get("engines", {}).get("kahya"), "| kadin:", body.get("engines", {}).get("kadin"),
      "| chatterbox:", body.get("engines", {}).get("chatterbox"))
