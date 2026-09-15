#!/usr/bin/env python3
"""F4 kanıtı: /api/sparks uçlarını Mac'ten yoklar. Token dosyadan okunur, ekrana basılmaz."""
import json
import re
import urllib.error
import urllib.request
from pathlib import Path

ENV = Path.home() / ".hermes" / ".env"


def env_map():
    out = {}
    for line in ENV.read_text().splitlines():
        m = re.match(r"^([A-Z0-9_]+)=(.*)$", line.strip())
        if m:
            out[m.group(1)] = m.group(2).strip().strip('"').strip("'")
    return out


E = env_map()
TOKEN = E.get("HERMES_DASHBOARD_SESSION_TOKEN", "")

CANDIDATES = [
    ("gateway-local", "http://127.0.0.1:9150/api/sparks", True),
    ("gateway-local-sparkapi", "http://127.0.0.1:9150/spark-api/api/sparks", True),
    ("gateway-local-sparkpath", "http://127.0.0.1:9150/spark/api/sparks", False),
    ("local-5555-direct", "http://127.0.0.1:5555/api/sparks", False),
    ("node1-5555", "http://192.168.1.99:5555/api/sparks", False),
    ("srv101-5555", "http://192.168.1.101:5555/api/sparks", False),
    ("srv101-9150", "http://192.168.1.101:9150/api/sparks", True),
    ("srv101-9150-status", "http://192.168.1.101:9150/api/status", True),
    ("ext-no-token", "https://hermes.winterfell07.keenetic.pro/api/sparks", False),
    ("ext-token", "https://hermes.winterfell07.keenetic.pro/api/sparks", True),
    ("ext-sparkapi", "https://hermes.winterfell07.keenetic.pro/spark-api/api/sparks", True),
]

rows = []
for name, url, use_token in CANDIDATES:
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/json")
    if use_token and TOKEN:
        req.add_header("X-Hermes-Session-Token", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            body = r.read(300).decode("utf-8", "replace")
            rows.append((name, url, r.status, body.replace("\n", " ")[:200]))
    except urllib.error.HTTPError as e:
        try:
            body = e.read(200).decode("utf-8", "replace")
        except Exception:
            body = ""
        rows.append((name, url, e.code, body.replace("\n", " ")[:200]))
    except Exception as e:  # noqa: BLE001
        rows.append((name, url, -1, f"{type(e).__name__}: {e}"))

print(f"token_present={bool(TOKEN)}")
for name, url, code, body in rows:
    print(f"{name:24s} {code:>4} {url}\n    {body}")

Path("/tmp/tur10/spark-probe.json").write_text(
    json.dumps(
        [{"name": n, "url": u, "code": c, "body": b} for n, u, c, b in rows],
        indent=2,
        ensure_ascii=False,
    )
)
