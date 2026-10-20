#!/usr/bin/env python3
"""V5-5 — canlı e2e kanıt betiği (elle çalıştırılır).

Gerçek HTTP sunucusu + geçici SQLite ile akışı yürütür ve her adımın kanıtını
JSON olarak stdout'a yazar:

    python3 backend/bot_framework/e2e_demo.py

Kabul: şablon → bot oluştur → sohbet (persona görünür) → ikinci tur →
export/import → sil.
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
import threading
import urllib.request
from typing import Any, Dict, Optional, Tuple

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bot_framework.api import make_server  # noqa: E402
from bot_framework.store import BotStore  # noqa: E402


def call(
    base: str, method: str, path: str, payload: Optional[Dict[str, Any]] = None
) -> Tuple[int, Any]:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Content-Type": "application/json"} if data else {}
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=15) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, (json.loads(raw) if raw else None)


def main() -> int:
    evidence = {"steps": []}
    with tempfile.TemporaryDirectory() as tmp:
        db = os.path.join(tmp, "bots.db")
        store = BotStore(db)
        httpd = make_server(store, host="127.0.0.1", port=0)
        base = "http://127.0.0.1:%d" % httpd.server_address[1]
        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        try:
            status, tpl = call(base, "GET", "/api/templates")
            evidence["steps"].append(
                {"step": "templates", "status": status, "count": len(tpl["templates"]),
                 "keys": [t["key"] for t in tpl["templates"]]}
            )

            status, created = call(
                base, "POST", "/api/bots/from-template",
                {"key": "kod", "overrides": {"name": "E2E Kod Botu"}},
            )
            bot = created["bot"]
            evidence["steps"].append(
                {"step": "create_bot", "status": status, "bot_id": bot["id"],
                 "name": bot["name"], "tools": bot["tools"]}
            )

            status, first = call(base, "POST", "/api/bots/%s/chat" % bot["id"],
                                 {"message": "Merhaba, kendini tanıt"})
            evidence["steps"].append(
                {"step": "chat_first", "status": status,
                 "session_id": first["session_id"],
                 "persona_visible": "[BOT PERSONA]" in first["system_prompt"],
                 "first_message_role": first["messages"][0]["role"],
                 "reply": first["reply"][:160]}
            )

            status, second = call(
                base, "POST", "/api/bots/%s/chat" % bot["id"],
                {"message": "İkinci tur", "session_id": first["session_id"]},
            )
            evidence["steps"].append(
                {"step": "chat_second", "status": status,
                 "same_session": second["session_id"] == first["session_id"],
                 "message_count": len(second["messages"])}
            )

            status, stored = call(base, "GET", "/api/sessions/%s" % first["session_id"])
            evidence["steps"].append(
                {"step": "session_persisted", "status": status,
                 "persona_visible": "[BOT PERSONA]" in stored["session"]["system_prompt"],
                 "message_count": len(stored["session"]["messages"])}
            )

            status, export = call(base, "GET", "/api/bots/%s/export" % bot["id"])
            status_imp, imported = call(base, "POST", "/api/bots/import", export)
            evidence["steps"].append(
                {"step": "export_import", "export_status": status, "import_status": status_imp,
                 "format": export["format"], "id_changed": imported["bot"]["id"] != bot["id"],
                 "same_persona": imported["bot"]["persona"] == bot["persona"]}
            )

            status, _ = call(base, "DELETE", "/api/bots/%s" % bot["id"])
            evidence["steps"].append({"step": "delete_bot", "status": status})
        finally:
            httpd.shutdown()
            httpd.server_close()
            thread.join(timeout=5)
            store.close()

    evidence["ok"] = all(
        all(s.get(k, True) for k in ("persona_visible", "same_session", "id_changed", "same_persona") if k in s)
        and all(
            s[k] in (200, 201, 204)
            for k in ("status", "export_status", "import_status")
            if k in s
        )
        for s in evidence["steps"]
    )
    print(json.dumps(evidence, ensure_ascii=False, indent=2))
    return 0 if evidence["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
